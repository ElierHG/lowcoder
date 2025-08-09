package org.example.environments.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagedObject {
    private String id;
    private String managedId;
    private String objGid;
    private String environmentId;
    private String objType; // ORG | APP | QUERY | DATASOURCE
}