package org.vivecraft.common.network.packet.c2s;

import net.minecraft.network.FriendlyByteBuf;
import org.vivecraft.common.network.packet.PayloadIdentifier;

/**
 * holds if the client is in tabletop mode, where it reaches into a small copy of the world in front of it
 *
 * @param active if the client is in tabletop mode
 * @param reach  how many blocks of extra reach the client would like to have, around its player
 */
public record TabletopPayloadC2S(boolean active, float reach) implements VivecraftPayloadC2S {

    @Override
    public PayloadIdentifier payloadId() {
        return PayloadIdentifier.TABLETOP;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(payloadId().ordinal());
        buffer.writeBoolean(this.active);
        buffer.writeFloat(this.reach);
    }

    public static TabletopPayloadC2S read(FriendlyByteBuf buffer) {
        return new TabletopPayloadC2S(buffer.readBoolean(), buffer.readFloat());
    }
}
