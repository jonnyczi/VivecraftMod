package org.vivecraft.common.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * indicates that the server supports tabletop mode
 *
 * @param allowed  if tabletop players get extra reach
 * @param maxReach the most extra reach in blocks, that the server gives tabletop players
 */
public record TabletopPayloadS2C(boolean allowed, float maxReach) implements VivecraftPayloadS2C {

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.TABLETOP;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        buffer.writeBoolean(this.allowed);
        buffer.writeFloat(this.maxReach);
    }

    public static TabletopPayloadS2C read(FriendlyByteBuf buffer) {
        return new TabletopPayloadS2C(buffer.readBoolean(), buffer.readFloat());
    }
}
