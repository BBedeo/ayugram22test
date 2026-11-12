package org.telegram.utils.glass;

import java.util.Objects;

public class GlassSettings {

    public final boolean isBlurEnabled;
    public final boolean isLiquidGlassEnabled;
    /** -1 when ExFullGram's optional mode is off, otherwise 0..2. */
    public final int liquidMaterial;

    public GlassSettings(boolean isBlurEnabled, boolean isLiquidGlassEnabled) {
        this(isBlurEnabled, isLiquidGlassEnabled, -1);
    }

    public GlassSettings(boolean isBlurEnabled, boolean isLiquidGlassEnabled, int liquidMaterial) {
        this.isBlurEnabled = isBlurEnabled;
        this.isLiquidGlassEnabled = isLiquidGlassEnabled;
        this.liquidMaterial = liquidMaterial;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GlassSettings)) {
            return false;
        }

        GlassSettings settings = (GlassSettings) object;
        return isBlurEnabled == settings.isBlurEnabled
                && isLiquidGlassEnabled == settings.isLiquidGlassEnabled
                && liquidMaterial == settings.liquidMaterial;
    }

    @Override
    public int hashCode() {
        return Objects.hash(isBlurEnabled, isLiquidGlassEnabled, liquidMaterial);
    }
}