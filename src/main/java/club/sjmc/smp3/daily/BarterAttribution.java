package club.sjmc.smp3.daily;

import java.util.UUID;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

public final class BarterAttribution {
	public static final AttachmentType<UUID> OWNER =
	    AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath("smp3", "barter_owner"), UUIDUtil.CODEC);
	private BarterAttribution() {}
	public static void register() {}
}
