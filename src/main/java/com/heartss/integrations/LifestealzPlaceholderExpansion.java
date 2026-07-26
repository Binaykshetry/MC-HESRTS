package com.heartss.integrations;

import com.heartss.Heartss;
import org.jetbrains.annotations.NotNull;

public class LifestealzPlaceholderExpansion extends HeartsPlaceholderExpansion {
    
    public LifestealzPlaceholderExpansion(Heartss plugin) {
        super(plugin);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lifestealz";
    }
}
