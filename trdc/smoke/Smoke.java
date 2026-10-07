import com.osohq.oso.Exceptions;
import com.osohq.oso.Oso;
import com.osohq.oso.Query;
import com.osohq.oso.Variable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks a packaged oso jar on the running platform: class registration, attribute and method
 * callbacks, tenant isolation, query iteration, errors, and the release of native handles. It
 * prints one line per decision and the SHA-256 of all lines, so two platforms compare by digest.
 *
 *   java -cp oso.jar trdc/smoke/Smoke.java [transcript-file]
 */
public class Smoke {

  public static class Tenant {
    public final String id;

    public Tenant(String id) {
      this.id = id;
    }
  }

  public static class User {
    public final String name;
    public final Tenant tenant;
    private final Set<String> roles;

    public User(String name, Tenant tenant, String... roles) {
      this.name = name;
      this.tenant = tenant;
      this.roles = Set.of(roles);
    }

    public boolean hasRole(String role) {
      return roles.contains(role);
    }

    public List<String> roleList() {
      return new ArrayList<>(new TreeSet<>(roles));
    }
  }

  public static class Document {
    public final String title;
    public final Tenant tenant;
    public final String owner;
    public final boolean archived;

    public Document(String title, Tenant tenant, String owner, boolean archived) {
      this.title = title;
      this.tenant = tenant;
      this.owner = owner;
      this.archived = archived;
    }

    public boolean ownedBy(User user) {
      return owner.equals(user.name);
    }
  }

  static final String POLICY =
      """
      allow(user: User, "read", doc: Document) if
        user.tenant.id = doc.tenant.id and
        (user.hasRole("reader") or user.hasRole("editor") or doc.ownedBy(user));

      allow(user: User, "edit", doc: Document) if
        user.tenant.id = doc.tenant.id and
        not doc.archived and
        (user.hasRole("editor") or doc.ownedBy(user));

      allow(user: User, "delete", doc: Document) if
        user.tenant.id = doc.tenant.id and user.hasRole("admin");

      role_of(user: User, role) if role in user.roleList();

      fails(user: User) if user.noSuchMethod();
      """;

  private final List<String> lines = new ArrayList<>();
  private int failures = 0;

  private void check(String name, Object actual, Object expected) {
    String line = name + " = " + actual;
    lines.add(line);
    if (!expected.equals(actual)) {
      failures++;
      System.out.println("FAIL " + line + " (expected " + expected + ")");
    } else {
      System.out.println("ok   " + line);
    }
  }

  public static void main(String[] args) throws Exception {
    Smoke smoke = new Smoke();
    smoke.run();
    String transcript = String.join("\n", smoke.lines) + "\n";
    String digest =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(transcript.getBytes(StandardCharsets.UTF_8)));
    if (args.length > 0) {
      Files.writeString(Path.of(args[0]), transcript);
    }
    System.out.println(
        "platform "
            + System.getProperty("os.name")
            + " "
            + System.getProperty("os.arch")
            + " java "
            + System.getProperty("java.version"));
    System.out.println("transcript sha256 " + digest);
    if (smoke.failures > 0) {
      System.out.println(smoke.failures + " failures");
      System.exit(1);
    }
  }

  void run() throws Exception {
    Oso oso = newOso();

    Tenant a = new Tenant("tenant-a");
    Tenant b = new Tenant("tenant-b");
    User reader = new User("rita", a, "reader");
    User editor = new User("ed", a, "editor");
    User admin = new User("ada", a, "admin", "editor");
    User owner = new User("olga", a);
    User foreignAdmin = new User("fritz", b, "admin", "editor", "reader");
    Document doc = new Document("plan", a, "olga", false);
    Document archived = new Document("old", a, "olga", true);

    check("reader read", oso.isAllowed(reader, "read", doc), true);
    check("reader edit", oso.isAllowed(reader, "edit", doc), false);
    check("editor edit", oso.isAllowed(editor, "edit", doc), true);
    check("editor edit archived", oso.isAllowed(editor, "edit", archived), false);
    check("owner read by method callback", oso.isAllowed(owner, "read", doc), true);
    check("owner edit by method callback", oso.isAllowed(owner, "edit", doc), true);
    check("admin delete", oso.isAllowed(admin, "delete", doc), true);
    check("editor delete", oso.isAllowed(editor, "delete", doc), false);
    check("unknown action", oso.isAllowed(admin, "publish", doc), false);
    check("foreign tenant read", oso.isAllowed(foreignAdmin, "read", doc), false);
    check("foreign tenant edit", oso.isAllowed(foreignAdmin, "edit", doc), false);
    check("foreign tenant delete", oso.isAllowed(foreignAdmin, "delete", doc), false);
    check("unregistered resource", oso.isAllowed(admin, "read", "plan"), false);
    check(
        "allowed actions admin",
        new TreeSet<>(oso.authorizedActions(admin, doc)).toString(),
        "[delete, edit, read]");
    check(
        "allowed actions foreign",
        new TreeSet<>(oso.authorizedActions(foreignAdmin, doc)).toString(),
        "[]");

    Query roles = oso.queryRule("role_of", admin, new Variable("role"));
    List<String> found = new ArrayList<>();
    while (roles.hasMoreElements()) {
      HashMap<String, Object> bindings = roles.nextElement();
      found.add((String) bindings.get("role"));
    }
    check("query iteration", found.toString(), "[admin, editor]");
    check("query once", oso.queryRuleOnce("role_of", reader, "reader"), true);
    check("query no result", oso.queryRule("role_of", reader, "admin").results().size(), 0);

    check("runtime error is raised", errorOf(() -> oso.queryRuleOnce("fails", reader)), "error");
    check(
        "parse error is raised",
        errorOf(
            () -> {
              Oso broken = new Oso();
              broken.loadStr("allow(actor, action, resource) if");
              return true;
            }),
        "error");
    check("decision after errors", oso.isAllowed(reader, "read", doc), true);

    check("native handles released", releasesNativeHandles(a), true);
  }

  interface Call {
    Object call() throws Exception;
  }

  private static String errorOf(Call call) {
    try {
      return "no error: " + call.call();
    } catch (Exceptions.OsoException e) {
      return "error";
    } catch (Exception e) {
      return "other " + e.getClass().getSimpleName();
    }
  }

  private static Oso newOso() throws Exceptions.OsoException {
    Oso oso = new Oso();
    oso.registerClass(Tenant.class, "Tenant");
    oso.registerClass(User.class, "User");
    oso.registerClass(Document.class, "Document");
    oso.loadStr(POLICY);
    return oso;
  }

  /**
   * Creates many Polar instances and queries and lets the finalizers free them. The resident set
   * of the process must not grow with each round.
   */
  @SuppressWarnings("removal")
  private static boolean releasesNativeHandles(Tenant tenant) throws Exception {
    User user = new User("u", tenant, "reader");
    Document doc = new Document("d", tenant, "x", false);
    long first = 0;
    long last = 0;
    for (int round = 0; round < 6; round++) {
      for (int i = 0; i < 200; i++) {
        Oso oso = newOso();
        for (int q = 0; q < 20; q++) {
          if (!oso.isAllowed(user, "read", doc)) {
            return false;
          }
        }
      }
      System.gc();
      System.runFinalization();
      System.gc();
      long rss = residentKilobytes();
      if (round == 1) {
        first = rss;
      }
      last = rss;
    }
    System.out.println("     resident set after round 2: " + first + " kB, after round 6: " + last + " kB");
    return first == 0 || last < first * 3 / 2;
  }

  private static long residentKilobytes() throws Exception {
    Path status = Path.of("/proc/self/status");
    if (!Files.exists(status)) {
      return 0;
    }
    for (String line : Files.readAllLines(status)) {
      if (line.startsWith("VmRSS:")) {
        return Long.parseLong(line.replaceAll("[^0-9]", ""));
      }
    }
    return 0;
  }
}
