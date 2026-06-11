package com.forensic.audit.commons;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata<T> {

    private T payload;
    private String sessionId;
    private String ipAddress;
    private String location;
    private long pageDwellTime;
    private int tabSwitchCount;
    private int windowBlurCount;
    private List<Keystroke> keystrokes;
    private List<MouseEvent> mouseEvents;
    private List<Click> clicks;
    private List<PageNavigation> pageNavigations;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Keystroke {
        private String key;
        private String type;
        private String target;
        private long timestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MouseEvent {
        private String type;
        private String target;
        private int x;
        private int y;
        private Integer button;
        private long timestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Click {
        private String target;
        private int x;
        private int y;
        private long timestamp;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PageNavigation {
        private String type;
        private String fromPage;
        private long timestamp;
    }
}
