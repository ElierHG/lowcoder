package org.example.environments.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {
    private String environmentId;
    private String environmentName;
    private String environmentDescription;
    private String environmentIcon;
    private String environmentType;
    private String environmentApiServiceUrl;
    private String environmentNodeServiceUrl;
    private String environmentFrontendUrl;
    private String environmentApikey;
    private boolean isMaster;
    private String createdAt;
    private String updatedAt;
}