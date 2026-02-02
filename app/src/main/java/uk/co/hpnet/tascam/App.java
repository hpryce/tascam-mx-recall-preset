package uk.co.hpnet.tascam;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.hpnet.tascam.client.TascamClient;
import uk.co.hpnet.tascam.client.TascamTcpClient;
import uk.co.hpnet.tascam.config.Config;
import uk.co.hpnet.tascam.model.Preset;

import java.io.Console;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Tascam MX Preset Tool - CLI for listing and recalling presets.
 */
@Command(name = "tascam-preset", 
         mixinStandardHelpOptions = true,
         version = "1.0",
         description = "List and recall presets on Tascam MX-DCP series mixers",
         subcommands = {App.ListCommand.class, App.RecallCommand.class})
public class App implements Callable<Integer> {

    private static final int DEFAULT_PORT = 54726;

    @Option(names = {"-d", "--debug"}, description = "Enable debug output (raw protocol messages)")
    private boolean debug;

    @Command(name = "list", description = "List all presets", mixinStandardHelpOptions = true)
    static class ListCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        private App parent;

        @Option(names = {"--host"}, description = "Mixer hostname or IP address")
        private String host;

        @Option(names = {"-p", "--port"}, description = "Mixer port (default: 54726)")
        private Integer port;

        @Override
        public Integer call() {
            Config config = Config.load();
            
            String effectiveHost = host != null ? host : config.host().orElse(null);
            if (effectiveHost == null) {
                System.err.println("Error: --host is required (or set host in ~/.tascam-preset.conf)");
                return 1;
            }
            
            int effectivePort = port != null ? port : config.port().orElse(DEFAULT_PORT);
            String password = config.password().orElseGet(App::promptForPassword);
            
            try (TascamClient client = new TascamTcpClient(0)) {
                client.connect(effectiveHost, effectivePort, password);
                
                List<Preset> presets = client.listPresets();
                Optional<Preset> current = client.getCurrentPreset();
                
                if (presets.isEmpty()) {
                    System.out.println("No presets found.");
                    return 0;
                }
                
                int currentNumber = current.map(Preset::number).orElse(-1);
                
                for (Preset preset : presets) {
                    String marker = (preset.number() == currentNumber) ? "*" : " ";
                    String lockIndicator = preset.locked()
                        .map(locked -> locked ? " [locked]" : "")
                        .orElse("");
                    System.out.printf("%s%2d: \"%s\"%s%n", 
                        marker, 
                        preset.number(), 
                        preset.name(),
                        lockIndicator);
                }
                
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "recall", description = "Recall (load) a preset by name", mixinStandardHelpOptions = true)
    static class RecallCommand implements Callable<Integer> {

        private static final Logger logger = LogManager.getLogger(RecallCommand.class);

        @CommandLine.ParentCommand
        private App parent;

        @Option(names = {"--host"}, description = "Mixer hostname or IP address")
        private String host;

        @Option(names = {"-p", "--port"}, description = "Mixer port (default: 54726)")
        private Integer port;

        @Option(names = {"-w", "--wait"}, defaultValue = "5", 
                description = "Seconds to wait before verification (0 to skip verification, default: 5)")
        private double waitSeconds;

        @Parameters(index = "0", description = "Preset name to recall")
        private String presetName;

        @Override
        public Integer call() {
            Config config = Config.load();
            
            String effectiveHost = host != null ? host : config.host().orElse(null);
            if (effectiveHost == null) {
                System.err.println("Error: --host is required (or set host in ~/.tascam-preset.conf)");
                return 1;
            }
            
            int effectivePort = port != null ? port : config.port().orElse(DEFAULT_PORT);
            String password = config.password().orElseGet(App::promptForPassword);
            
            long waitMs = (long) (waitSeconds * 1000);
            
            try (TascamClient client = new TascamTcpClient(waitMs)) {
                client.connect(effectiveHost, effectivePort, password);
                
                // Find preset by name
                List<Preset> presets = client.listPresets();
                Optional<Preset> match = presets.stream()
                    .filter(p -> p.name().equalsIgnoreCase(presetName))
                    .findFirst();
                
                if (match.isEmpty()) {
                    logger.debug("Available presets: {}", presets);
                    System.err.println("Error: No preset found with name \"" + presetName + "\"");
                    return 1;
                }
                
                Preset preset = match.get();
                client.recallPreset(preset.number());
                System.out.println("Recalled preset " + preset.number() + ": \"" + preset.name() + "\"");
                
                return 0;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    private static String promptForPassword() {
        Console console = System.console();
        if (console != null) {
            char[] passwordChars = console.readPassword("Password (press Enter if none): ");
            return passwordChars == null ? "" : new String(passwordChars);
        } else {
            // Fallback for environments without console (e.g., IDE, tests)
            System.out.print("Password (press Enter if none): ");
            Scanner scanner = new Scanner(System.in);
            if (scanner.hasNextLine()) {
                return scanner.nextLine();
            }
            return "";
        }
    }

    @Override
    public Integer call() {
        // No subcommand specified, print help
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        System.exit(exitCode);
    }

    /**
     * Run the CLI with the given arguments. Used by tests.
     */
    public static int run(String[] args) {
        App app = new App();
        CommandLine cmd = new CommandLine(app);
        
        // Pre-parse to check for debug flag
        if (Arrays.asList(args).contains("--debug") || Arrays.asList(args).contains("-d")) {
            System.setProperty("tascam.logLevel", "DEBUG");
            Configurator.reconfigure();
        }
        
        return cmd.execute(args);
    }
}
