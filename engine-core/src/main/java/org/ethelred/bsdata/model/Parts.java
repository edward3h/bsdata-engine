/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;

/** Small records shared by the catalogue model. */
public final class Parts {
    private Parts() {}

    public record CostType(String id, String name, double defaultLimit, boolean hidden) {}

    public record Cost(String typeId, String name, double value) {}

    public record Characteristic(String name, String typeId, String text) {}

    public record Profile(
            String id,
            String name,
            String typeId,
            String typeName,
            boolean hidden,
            List<Characteristic> characteristics,
            Raw raw) {}

    public record Rule(String id, String name, String description, boolean hidden) {}

    public record InfoLink(String id, String name, String targetId, String type, boolean hidden) {}

    public record CategoryLink(String id, String name, String targetId, boolean primary, boolean hidden) {}

    public record Category(String id, String name, boolean hidden, Raw raw) {}

    public record CharacteristicType(String id, String name) {}

    public record ProfileType(String id, String name, List<CharacteristicType> characteristicTypes) {}

    public record CatalogueLink(String id, String name, String targetId, boolean importRootEntries) {}

    public record ForceEntry(
            String id,
            String name,
            boolean hidden,
            List<CategoryLink> categoryLinks,
            List<ForceEntry> children,
            Raw raw) {}
}
