package uk.co.hpnet.tascam;

import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import uk.co.hpnet.tascam.client.TascamClient;
import uk.co.hpnet.tascam.client.TascamTcpClient;
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
         description = "List and recall presets on Tascam MX-DCP series mixers")
public class App implements Callable<Integer> {

    @Option(names = {"-d", "--debug"}, description = "Enable debug output (raw protocol messages)")
    private boolean debug;

    @Command(name = "list", description = "List all presets", mixinStandardHelpOptions = true)
    static class ListCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        private App parent;

        @Option(names = {"--host"}, required = true, description = "Mixer hostname or IP address")
        private String host;

        @Option(names = {"-p", "--port"}, defaultValue = "54726", description = "Mixer port (default: 54726)")
        private int port;

        @Override
        public Integer call() {
            String password = promptForPassword();
            
            try (TascamClient client = new TascamTcpClient()) {
                client.connect(host, port, password);
                
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

        @CommandLine.ParentCommand
        private App parent;

        @Option(names = {"--host"}, required = true, description = "Mixer hostname or IP address")
        private String host;

        @Option(names = {"-p", "--port"}, defaultValue = "54726", description = "Mixer port (default: 54726)")
        private int port;

        @Parameters(index = "0", description = "Preset name to recall")
        private String presetName;

        @Override
        public Integer call() {
            String password = promptForPassword();
            
            try (TascamClient client = new TascamTcpClient()) {
                client.connect(host, port, password);
                
                // Find preset by name
                List<Preset> presets = client.listPresets();
                Optional<Preset> match = presets.stream()
                    .filter(p -> p.name().equalsIgnoreCase(presetName))
                    .findFirst();
                
                if (match.isEmpty()) {
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
            // Fallback for environments without console (e.g., IDE)
            System.out.print("Password (press Enter if none): ");
            Scanner scanner = new Scanner(System.in);
            return scanner.nextLine();
        }
    }

    @Override
    public Integer call() {
        // Default: show help
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Executes the CLI with the given arguments and returns the exit code.
     * Use this for testing; main() calls this then System.exit().
     */
    static int run(String[] args) {
        // Set log level BEFORE Log4j initializes (reads ${sys:tascam.logLevel} from log4j2.xml)
        if (Arrays.asList(args).contains("-d") || Arrays.asList(args).contains("--debug")) {
            System.setProperty("tascam.logLevel", "DEBUG");
        } else {
            System.setProperty("tascam.logLevel", "WARN");
        }
        // Force Log4j2 to re-read configuration with updated system property
        Configurator.reconfigure();
        
        return new CommandLine(new App())
                .addSubcommand("list", new ListCommand())
                .addSubcommand("recall", new RecallCommand())
                .execute(args);
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }
}
