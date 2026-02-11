package com.roften.avilixeconomy.shop.render;

/**
 * Persistent transform override applied on top of the shop shelf render.
 *
 * All values are in block units (offsets) and degrees (rotations).
 */
public record RenderTransform(
        float offX,
        float offY,
        float offZ,
        float rotX,
        float rotY,
        float rotZ,
        float scaleMul,
        float extraLift
) {
    public static final RenderTransform IDENTITY = new RenderTransform(0, 0, 0, 0, 0, 0, 1.0f, 0);

    public RenderTransform withOff(float x, float y, float z) {
        return new RenderTransform(x, y, z, rotX, rotY, rotZ, scaleMul, extraLift);
    }

    public RenderTransform withRot(float x, float y, float z) {
        return new RenderTransform(offX, offY, offZ, x, y, z, scaleMul, extraLift);
    }

    public RenderTransform withScale(float s) {
        return new RenderTransform(offX, offY, offZ, rotX, rotY, rotZ, s, extraLift);
    }

    public RenderTransform withLift(float lift) {
        return new RenderTransform(offX, offY, offZ, rotX, rotY, rotZ, scaleMul, lift);
    }
}
