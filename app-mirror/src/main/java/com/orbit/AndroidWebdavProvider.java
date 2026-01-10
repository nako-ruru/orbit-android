package com.orbit;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;

import aar.WebDAVServerProvider;
import de.sty.fileserv.core.Authenticator;
import de.sty.fileserv.core.FileAuthenticator;
import de.sty.fileserv.core.FileServVersion;
import de.sty.fileserv.core.MultiAuthenticator;
import de.sty.fileserv.core.SimpleAuthenticator;
import de.sty.fileserv.core.WebDavServer;
import picocli.CommandLine;

// version is set by maven via filtering
public class AndroidWebdavProvider implements WebDAVServerProvider {

    private final Context context;

    public AndroidWebdavProvider(Context context) {
        this.context = context;
    }
    @Override
    public void startWebdavServer(long port) throws Exception {
        new Thread(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String url = prefs.getString("dir_uri", null);
            FileServApp.main(new String[] {url, "--http-port", Long.toString(port)});
        }).start();
    }

    @Override
    public void stopWebdavServer() throws Exception {
        if(FileServApp.server != null) {
            FileServApp.server.stop();
        }
    }
}

@CommandLine.Command(
        name = "fileserv",
        mixinStandardHelpOptions = true,
        versionProvider = de.sty.fileserv.FileServApp.VersionProvider.class,
        description = {"A simple WebDAV server."}
)
class FileServApp implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(FileServApp.class);
    public static final String VERSION;

    static Server server;

    @CommandLine.Option(
            names = {"--config"},
            description = {"Path to a configuration properties file"},
            defaultValue = "fileserv.properties"
    )
    Path configFile;
    @CommandLine.Parameters(
            index = "0",
            description = {"Data directory to serve"},
            defaultValue = "./data"
    )
    Path root;
    @CommandLine.Option(
            names = {"--https-port"},
            description = {"HTTPS port"},
            defaultValue = "8443"
    )
    int httpsPort;
    @CommandLine.Option(
            names = {"--http-port"},
            description = {"HTTP port (set to 0 to disable)"},
            defaultValue = "8080"
    )
    int httpPort;
    @CommandLine.Option(
            names = {"-u", "--user"},
            description = {"User name for authentication. Must followed by password."}
    )
    private List<String> users;
    @CommandLine.Option(
            names = {"-p", "--password"},
            description = {"Password for authentication. A username must be given prior."}
    )
    private List<String> passwords;
    @CommandLine.Option(
            names = {"--keystore"},
            description = {"Path to the keystore file with SSL certificate"},
            defaultValue = "keystore.p12"
    )
    private String keyStorePath;
    @CommandLine.Option(
            names = {"--keystore-password"},
            description = {"Keystore password"},
            defaultValue = "changeit"
    )
    private String keyStorePassword;
    @CommandLine.Option(
            names = {"--key-pass"},
            description = {"Key password (defaults to keystore password)"}
    )
    private String keyPassword;
    @CommandLine.Option(
            names = {"--behind-proxy"},
            description = {"Trust X-Forwarded-* headers"},
            defaultValue = "true"
    )
    private boolean behindProxy;
    @CommandLine.Option(
            names = {"--passwd"},
            description = {"Path to a passwords file"}
    )
    private Path passwordsPath;

    public static void main(String[] args) {
        FileServApp app = new FileServApp();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(args);
        Path configPath = app.configFile;
        cmd.setDefaultValueProvider(new FileServApp.PropertiesDefaultProvider(configPath));
        int exitCode = cmd.execute(args);
    }

    public Integer call() throws Exception {
        Authenticator authenticator = this.createAuthenticator();
        this.root = this.root.toAbsolutePath().normalize();
        if (this.keyPassword == null) {
            this.keyPassword = this.keyStorePassword;
        }

        WebDavServer.Config cfg = new WebDavServer.Config(this.root, this.behindProxy, this.httpPort, this.httpsPort, this.keyStorePath, this.keyStorePassword, this.keyPassword, authenticator);
        server = WebDavServer.build(cfg);
        LOG.info("Starting FileServ with configuration:");
        LOG.info("  HTTPS: {}://localhost:{}/", "https", this.httpsPort);
        if (this.httpPort > 0) {
            LOG.info("  HTTP : {}://localhost:{}/", "http", this.httpPort);
        } else {
            LOG.info("  HTTP : disabled");
        }

        LOG.info("  Root: {}", this.root);
        LOG.info("  Auth: {}", authenticator);
        LOG.info("  behindProxy={}", this.behindProxy);
        server.start();
        LOG.info("Jetty server started.");
        server.join();
        return 0;
    }

    Authenticator createAuthenticator() {
        List<Authenticator> authenticators = new ArrayList();
        Path passwdFile = this.passwordsPath;
        if (passwdFile == null) {
            Path defaultPasswd = Paths.get("passwd");
            if (Files.exists(defaultPasswd, new LinkOption[0])) {
                passwdFile = defaultPasswd;
            }
        }

        if (passwdFile != null) {
            authenticators.add(new FileAuthenticator(passwdFile));
            LOG.info("AUTH: Use usernames/passwords from file: {}", passwdFile);
        }

        if (this.users != null && !this.users.isEmpty()) {
            for(int i = 0; i < this.users.size(); ++i) {
                String u = (String)this.users.get(i);
                String p = this.passwords != null && i < this.passwords.size() ? (String)this.passwords.get(i) : "";
                authenticators.add(new SimpleAuthenticator(u, p));
                LOG.info("Added authentication for user: {}", u);
            }
        }

        if (authenticators.isEmpty()) {
            int randomPassword = 100000 + (new Random()).nextInt(900000);
            String password = String.valueOf(randomPassword);
            authenticators.add(new SimpleAuthenticator("demo", password));
            LOG.info("No authentication configured. Using user 'demo', password '{}'", password);
        }

        return new MultiAuthenticator(authenticators);
    }

    static {
        VERSION = FileServVersion.VERSION;
    }

    public static class PropertiesDefaultProvider implements CommandLine.IDefaultValueProvider {
        private final Properties properties = new Properties();

        public PropertiesDefaultProvider(Path configFile) {
            if (configFile != null && Files.exists(configFile, new LinkOption[0])) {
                try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                    this.properties.load(reader);
                } catch (IOException e) {
                    FileServApp.LOG.warn("Failed to load configuration from {}: {}", configFile, e.getMessage());
                }
            }

        }

        public String defaultValue(CommandLine.Model.ArgSpec argSpec) {
            String name = null;
            if (argSpec.isOption()) {
                name = ((CommandLine.Model.OptionSpec)argSpec).longestName();
            } else if (argSpec.isPositional()) {
                name = argSpec.paramLabel();
            }

            if (name == null) {
                return null;
            } else {
                while(name.startsWith("-")) {
                    name = name.substring(1);
                }

                String sysProp = System.getProperty("fileserv." + name);
                if (sysProp != null) {
                    return sysProp;
                } else {
                    String var10000 = name.toUpperCase();
                    String envVar = System.getenv("FILESERV_" + var10000.replace("-", "_"));
                    return envVar != null ? envVar : this.properties.getProperty(name);
                }
            }
        }
    }

}
