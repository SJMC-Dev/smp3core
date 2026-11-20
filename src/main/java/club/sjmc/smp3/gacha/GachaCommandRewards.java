package club.sjmc.smp3.gacha;

import com.mojang.brigadier.context.ContextChain;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class GachaCommandRewards {
	private GachaCommandRewards() {}
	static void validate(MinecraftServer server, String command) {
		if (command.isBlank() || command.startsWith("/") || command.length() > 8192 || command.contains("\n") ||
		    command.contains("\r"))
			throw new IllegalArgumentException("commands: one command without leading slash, at most 8192 characters");
		parse(server, expand(command, "SMP3", "00000000-0000-0000-0000-000000000000"),
		      server.createCommandSourceStack());
	}
	private static void parse(MinecraftServer server, String command, CommandSourceStack source) {
		var parsed = server.getCommands().getDispatcher().parse(command, source);
		if (parsed.getReader().canRead() || ContextChain.tryFlatten(parsed.getContext().build(command)).isEmpty())
			throw new IllegalArgumentException("commands: cannot parse " + command);
	}
	private static String expand(String command, String name, String uuid) {
		if (command.contains("%player%") && !name.matches("[A-Za-z0-9_]{1,16}"))
			throw new IllegalArgumentException("玩家名无法用于指令，请使用 %uuid%");
		return command.replace("%player%", name).replace("%uuid%", uuid);
	}
	static boolean execute(ServerPlayer player, String template) {
		var server = player.level().getServer();
		var outcome = new Outcome();
		var source = server.createCommandSourceStack()
		                 .withEntity(player)
		                 .withLevel(player.level())
		                 .withPosition(player.position())
		                 .withRotation(player.getRotationVector())
		                 .withSuppressedOutput()
		                 .withCallback(outcome);
		String command = expand(template, player.getGameProfile().name(), player.getUUID().toString());
		parse(server, command, source);
		server.getCommands().performPrefixedCommand(source, command);
		return outcome.seen && !outcome.failed;
	}
	public static final class Outcome implements CommandResultCallback {
		private boolean seen, failed;
		private final Outcome root;
		private final CommandResultCallback delegate;
		Outcome() {
			root = this;
			delegate = CommandResultCallback.EMPTY;
		}
		private Outcome(Outcome root, CommandResultCallback delegate) {
			this.root = root;
			this.delegate = delegate;
		}
		public Outcome preserving(CommandResultCallback delegate) { return new Outcome(root, delegate); }
		@Override
		public void onResult(boolean success, int value) {
			root.seen = true;
			root.failed |= !success || value <= 0;
			delegate.onResult(success, value);
		}
	}
}
