/*
* Aoba Hacked Client
* Copyright (C) 2019-2024 coltonk9043
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Nuker Module
 */
package net.aoba.module.modules.world;

import java.util.HashSet;
import org.lwjgl.glfw.GLFW;
import net.aoba.Aoba;
import net.aoba.event.events.BlockStateEvent;
import net.aoba.event.events.RenderEvent;
import net.aoba.event.events.TickEvent;
import net.aoba.event.listeners.BlockStateListener;
import net.aoba.event.listeners.RenderListener;
import net.aoba.event.listeners.TickListener;
import net.aoba.gui.colors.Color;
import net.aoba.misc.RenderUtils;
import net.aoba.module.Module;
import net.aoba.settings.types.BlocksSetting;
import net.aoba.settings.types.BooleanSetting;
import net.aoba.settings.types.ColorSetting;
import net.aoba.settings.types.FloatSetting;
import net.aoba.settings.types.KeybindSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class Nuker extends Module implements RenderListener, TickListener, BlockStateListener {
	private BooleanSetting creative = new BooleanSetting("nuker_creative", "Creative", "Creative", false);
	private ColorSetting color = new ColorSetting("nuker_color", "Color", "Color", new Color(0, 1f, 1f));
	private FloatSetting radius = new FloatSetting("nuker_radius", "Radius", "Radius", 5f, 0f, 15f, 1f);
	private BlocksSetting blacklist = new BlocksSetting("nuker_blacklist", "Blacklist",
			"Blocks that will not be broken by Nuker.", new HashSet<Block>());

	private BlockPos currentBlockToBreak = null;

	public Nuker() {
		super(new KeybindSetting("key.nuker", "Nuker Key", InputUtil.fromKeyCode(GLFW.GLFW_KEY_UNKNOWN, 0)));

		this.setName("Nuker");
		this.setCategory(Category.World);
		this.setDescription("Destroys blocks around the player.");

		this.addSetting(creative);
		this.addSetting(radius);
		this.addSetting(color);
		this.addSetting(blacklist);
	}

	public void setRadius(int radius) {
		this.radius.setValue((float) radius);
	}

	@Override
	public void onDisable() {
		Aoba.getInstance().eventManager.RemoveListener(RenderListener.class, this);
		Aoba.getInstance().eventManager.RemoveListener(TickListener.class, this);
		Aoba.getInstance().eventManager.RemoveListener(BlockStateListener.class, this);
	}

	@Override
	public void onEnable() {
		Aoba.getInstance().eventManager.AddListener(RenderListener.class, this);
		Aoba.getInstance().eventManager.AddListener(TickListener.class, this);
		Aoba.getInstance().eventManager.AddListener(BlockStateListener.class, this);
	}

	@Override
	public void onToggle() {
	}

	@Override
	public void OnUpdate(TickEvent event) {
		if(creative.getValue()) {
			int range = (int) (Math.floor(radius.getValue()) + 1);
            Iterable<BlockPos> blocks = BlockPos.iterateOutwards(new BlockPos(BlockPos.ofFloored(MC.player.getPos()).up()), range, range, range);
            for (BlockPos blockPos : blocks) {
				Block block = MC.world.getBlockState(blockPos).getBlock();
				if (block == Blocks.AIR || blacklist.getValue().contains(block))
					continue;
            	
				MC.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPos, Direction.NORTH));
				MC.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, blockPos, Direction.NORTH));
				MC.player.swingHand(Hand.MAIN_HAND);
            }
		}else {
			if (currentBlockToBreak == null) {
				currentBlockToBreak = getNextBlock();
			}
			
			if(currentBlockToBreak != null) {
				
				// Check to ensure that the block is not further than we can reach.
				int range = (int) (Math.floor(radius.getValue()) + 1);
				int rangeSqr = range ^ 2;
				if(MC.player.getBlockPos().toCenterPos().distanceTo(currentBlockToBreak.toCenterPos()) > rangeSqr) {
					currentBlockToBreak = null;
				}else {
					MC.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, currentBlockToBreak, Direction.NORTH));
					MC.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, currentBlockToBreak, Direction.NORTH));
					MC.player.swingHand(Hand.MAIN_HAND);
				}
			}
		}
	}

	@Override
	public void OnRender(RenderEvent event) {
		if(currentBlockToBreak != null) {
			RenderUtils.draw3DBox(event.GetMatrix().peek().getPositionMatrix(), new Box(currentBlockToBreak), color.getValue());
		}
	}

	@Override
	public void OnBlockStateChanged(BlockStateEvent event) {
		if(currentBlockToBreak != null) {
			BlockPos blockPos = event.getBlockPos();
			BlockState oldBlockState = event.getPreviousBlockState();
			if (blockPos.equals(currentBlockToBreak) && (oldBlockState.isAir())) {
				currentBlockToBreak = null;
			}
		}
	}
	
	private BlockPos getNextBlock() {
		// Scan to find next block to begin breaking.
		int rad = radius.getValue().intValue();
		for (int y = rad; y > -rad; y--) {
			for (int x = -rad; x < rad; x++) {
				for (int z = -rad; z < rad; z++) {
					BlockPos blockpos = new BlockPos(MC.player.getBlockX() + x, (int) MC.player.getBlockY() + y,
							(int) MC.player.getBlockZ() + z);
					Block block = MC.world.getBlockState(blockpos).getBlock();
					if (block == Blocks.AIR || blacklist.getValue().contains(block))
						continue;
					
					return blockpos;
				}
			}
		}
		return null;
	}
}
