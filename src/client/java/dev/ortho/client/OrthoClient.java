package dev.ortho.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.ortho.core.OrthoConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;


/** Client entrypoint: keys, events and the per-tick renderer. */
public final class OrthoClient implements ClientModInitializer {
	private static KeyMapping anchorKey;
	private static KeyMapping toggleKey;
	private static KeyMapping screenKey;
	private static KeyMapping layerKey;
	private static KeyMapping modifierKey;
	private static String lastDimension;
	private static boolean openScreen;

	public static void openScreenNextTick() {
		openScreen = true;
	}

	@Override
	public void onInitializeClient() {
		Ortho.load();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Ortho.MOD_ID, "main"));
		anchorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ortho.anchor", InputConstants.KEY_G, category));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ortho.toggle", InputConstants.KEY_H, category));
		screenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ortho.screen", InputConstants.KEY_J, category));
		layerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ortho.layer", InputConstants.UNKNOWN.getValue(), category));
		modifierKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ortho.modifier", InputConstants.KEY_LALT, category));

		ClientTickEvents.END_CLIENT_TICK.register(OrthoClient::tick);
		ClientHotbarScrollEvents.ALLOW.register((inventory, from, to, scrollX, scrollY) -> {
			if (!modifierKey.isDown()) {
				return true;
			}
			double delta = scrollY != 0 ? scrollY : scrollX;
			nudge(delta > 0 ? 1 : -1);
			return false;
		});
		UseBlockCallback.EVENT.register(MirrorMarks::onUse);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			lastDimension = null;
			Ortho.enterWorld(worldKey(mc));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			Ortho.leaveWorld();
			MirrorMarks.clear();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Ortho.saveNow());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> OrthoCommands.register(dispatcher));
		Ortho.LOG.info("Ortho loaded");
	}

	private static void tick(Minecraft mc) {
		Ortho.tickSave();
		if (mc.level == null || mc.player == null) {
			return;
		}
		String dim = mc.level.dimension().identifier().toString();
		if (!dim.equals(lastDimension)) {
			lastDimension = dim;
			Ortho.layer = null;
			Ortho.regenerate();
		}
		while (anchorKey.consumeClick()) {
			anchorAtTarget(mc);
		}
		while (toggleKey.consumeClick()) {
			Ortho.visible = !Ortho.visible;
			Ortho.say(Ortho.visible ? "stencil shown" : "stencil hidden");
		}
		while (layerKey.consumeClick()) {
			toggleLayer(mc);
		}
		while (screenKey.consumeClick()) {
			openScreen = true;
		}
		if (openScreen) {
			openScreen = false;
			mc.gui.setScreen(new OrthoScreen(null));
		}
		MirrorMarks.tick(mc);
		StencilRenderer.tick(mc);
	}

	static void anchorAtTarget(Minecraft mc) {
		BlockPos pos;
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos clicked = bh.getBlockPos();
			pos = mc.level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(bh.getDirection());
		} else {
			pos = mc.player.blockPosition();
		}
		Ortho.anchor(pos.getX(), pos.getY(), pos.getZ());
		Ortho.say("anchored " + Ortho.stencil().describe() + " at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	static void toggleLayer(Minecraft mc) {
		if (Ortho.layer != null) {
			Ortho.layer = null;
			Ortho.say("showing all layers");
		} else {
			Ortho.layer = mc.player.blockPosition().getY();
			Ortho.say("layer Y=" + Ortho.layer + " (" + Ortho.voxels.countOnLayer(Ortho.layer) + " blocks)");
		}
	}

	/** Modifier + scroll: moves the layer slice, or the anchor when layer mode is off. */
	static void nudge(int dy) {
		if (Ortho.layer != null) {
			Ortho.layer += dy;
			Ortho.say("layer Y=" + Ortho.layer + " (" + Ortho.voxels.countOnLayer(Ortho.layer) + " blocks)");
		} else if (Ortho.anchoredHere()) {
			OrthoConfig.WorldPlacement p = Ortho.placement();
			Ortho.anchor(p.x, p.y + dy, p.z);
			Ortho.say("anchor Y=" + p.y);
		}
	}

	/** Anchors and mirrors are saved per world: singleplayer save name or server address. */
	static String worldKey(Minecraft mc) {
		if (mc.getSingleplayerServer() != null) {
			return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		if (mc.getCurrentServer() != null) {
			return "mp:" + mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
		}
		return "other";
	}
}
