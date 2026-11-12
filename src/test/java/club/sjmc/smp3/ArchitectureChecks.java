package club.sjmc.smp3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ArchitectureChecks {
	public static void run() throws Exception {
		Pattern presentation = Pattern.compile("eu\\.pb4\\.sgui\\.|club\\.sjmc\\.smp3\\.(?:\\w+\\.ui\\.|gui\\.)");
		Pattern effects =
		    Pattern.compile("club\\.sjmc\\.smp3\\.(?:economy\\.|inventory\\.)|Permissions\\s*\\.\\s*grant\\s*\\(");
		try (var sources = Files.walk(Path.of("src/main/java"))) {
			for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
				String code = Files.readString(source);
				if (source.getFileName().toString().matches(".*(?:Service|Data|Store)\\.java")) {
					assert !presentation.matcher(code).find() : source + " depends on presentation";
				}
				if (source.getParent().getFileName().toString().equals("ui")) {
					assert !effects.matcher(code).find() : source + " bypasses service operations";
				}
			}
		}
	}
}
