/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.IContainerListener;
import net.minecraft.item.ItemStack;

import alexiil.mc.lib.attributes.item.FixedItemInv;

import appeng.api.config.SecurityPermissions;
import appeng.api.features.INetworkEncodable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.items.IBiometricCard;
import appeng.api.storage.ITerminalHost;
import appeng.container.SlotSemantic;
import appeng.container.guisync.GuiSync;
import appeng.container.me.items.ItemTerminalContainer;
import appeng.container.slot.OutputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.core.Api;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.SecurityStationTileEntity;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

/**
 * @see appeng.client.gui.implementations.SecurityStationScreen
 */
public class SecurityStationContainer extends ItemTerminalContainer implements IAEAppEngInventory {

    public static final ContainerType<SecurityStationContainer> TYPE = ContainerTypeBuilder
            .create(SecurityStationContainer::new, ITerminalHost.class)
            .requirePermission(SecurityPermissions.SECURITY)
            .build("securitystation");

    private final RestrictedInputSlot configSlot;

    private final RestrictedInputSlot wirelessIn;
    private final OutputSlot wirelessOut;

    private final SecurityStationTileEntity securityBox;
    @GuiSync(0)
    public int permissionMode = 0;

    public SecurityStationContainer(int id, final PlayerInventory ip, final ITerminalHost monitorable) {
        super(TYPE, id, ip, monitorable, false);

        this.securityBox = (SecurityStationTileEntity) monitorable;

        this.addSlot(this.configSlot = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.BIOMETRIC_CARD,
                this.securityBox.getConfigSlot(), 0), SlotSemantic.BIOMETRIC_CARD);

        AppEngInternalInventory wirelessEncoder = new AppEngInternalInventory(this, 2);
        this.addSlot(this.wirelessIn = new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODABLE_ITEM,
                wirelessEncoder, 0), SlotSemantic.MACHINE_INPUT);
        this.addSlot(this.wirelessOut = new OutputSlot(wirelessEncoder, 1, null), SlotSemantic.MACHINE_OUTPUT);

        this.createPlayerInventorySlots(ip);
    }

    public void toggleSetting(final String value, final PlayerEntity player) {
        try {
            final SecurityPermissions permission = SecurityPermissions.valueOf(value);

            final ItemStack a = this.configSlot.getStack();
            if (!a.isEmpty() && a.getItem() instanceof IBiometricCard) {
                final IBiometricCard bc = (IBiometricCard) a.getItem();
                if (bc.hasPermission(a, permission)) {
                    bc.removePermission(a, permission);
                } else {
                    bc.addPermission(a, permission);
                }
            }
        } catch (final EnumConstantNotPresentException ex) {
            // :(
        }
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.SECURITY, false);

        this.setPermissionMode(0);

        final ItemStack a = this.configSlot.getStack();
        if (!a.isEmpty() && a.getItem() instanceof IBiometricCard) {
            final IBiometricCard bc = (IBiometricCard) a.getItem();

            for (final SecurityPermissions sp : bc.getPermissions(a)) {
                this.setPermissionMode(this.getPermissionMode() | 1 << sp.ordinal());
            }
        }

        this.updatePowerStatus();

        super.detectAndSendChanges();
    }

    @Override
    public void onContainerClosed(final PlayerEntity player) {
        super.onContainerClosed(player);

        if (this.wirelessIn.getHasStack()) {
            player.dropItem(this.wirelessIn.getStack(), false);
        }

        if (this.wirelessOut.getHasStack()) {
            player.dropItem(this.wirelessOut.getStack(), false);
        }
    }

    @Override
    public void saveChanges() {
        // :P
    }

    @Override
    public void onChangeInventory(final FixedItemInv inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (!this.wirelessOut.getHasStack() && this.wirelessIn.getHasStack()) {
            final ItemStack term = this.wirelessIn.getStack().copy();
            INetworkEncodable networkEncodable = null;

            if (term.getItem() instanceof INetworkEncodable) {
                networkEncodable = (INetworkEncodable) term.getItem();
            }

            final IWirelessTermHandler wTermHandler = Api.instance().registries().wireless()
                    .getWirelessTerminalHandler(term);
            if (wTermHandler != null) {
                networkEncodable = wTermHandler;
            }

            if (networkEncodable != null) {
                networkEncodable.setEncryptionKey(term, String.valueOf(this.securityBox.getSecurityKey()), "");

                this.wirelessIn.putStack(ItemStack.EMPTY);
                this.wirelessOut.putStack(term);

                // update the two slots in question...
                for (final IContainerListener listener : this.listeners) {
                    listener.sendSlotContents(this, this.wirelessIn.slotNumber, this.wirelessIn.getStack());
                    listener.sendSlotContents(this, this.wirelessOut.slotNumber, this.wirelessOut.getStack());
                }
            }
        }
    }

    public int getPermissionMode() {
        return this.permissionMode;
    }

    private void setPermissionMode(final int permissionMode) {
        this.permissionMode = permissionMode;
    }

    public RestrictedInputSlot getConfigSlot() {
        return configSlot;
    }
}
