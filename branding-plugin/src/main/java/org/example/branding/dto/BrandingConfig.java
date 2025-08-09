package org.example.branding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingConfig {
    private String id;
    private String orgId; // empty string for global
    private String config_name;
    private String config_description;
    private String config_icon;
    // Important: this must be stringified JSON
    private String config_set;
}