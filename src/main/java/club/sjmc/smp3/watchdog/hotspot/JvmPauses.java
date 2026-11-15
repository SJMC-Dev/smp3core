package club.sjmc.smp3.watchdog.hotspot;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

public final class JvmPauses implements AutoCloseable {
	private final Recording recording = new Recording();
	public JvmPauses() {
		try {
			recording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO).withoutStackTrace();
			recording.setMaxSize(16 * 1024 * 1024);
			recording.setMaxAge(Duration.ofSeconds(70));
			recording.start();
		} catch (RuntimeException e) {
			recording.close();
			throw e;
		}
	}

	public List<HotspotReport.Pause> finish(HotspotRecorder recorder) throws IOException {
		recording.stop();
		var file = Files.createTempFile("smp3-watchdog-", ".jfr");
		try {
			recording.dump(file);
			List<HotspotReport.Pause> pauses = new ArrayList<>();
			try (var reader = new RecordingFile(file)) {
				while (reader.hasMoreEvents()) {
					var event = reader.readEvent();
					if (!event.getEventType().getName().equals("jdk.GCPhasePause"))
						continue;
					long start =
					    recorder.originNanos + Duration.between(recorder.originInstant, event.getStartTime()).toNanos();
					pauses.add(new HotspotReport.Pause(start, start + event.getDuration().toNanos()));
				}
			}
			return pauses;
		} finally {
			Files.deleteIfExists(file);
			recording.close();
		}
	}

	@Override
	public void close() {
		recording.close();
	}
}
