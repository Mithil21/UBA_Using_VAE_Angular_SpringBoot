package com.example.uba_research.common;

import java.time.LocalDateTime;
import java.util.List;

public class Metadata {
    private List<KeyPress> keysPressed;
    private List<MouseClick> mouseClicks;
    private List<MouseHover> mouseHovers;
    private List<ScrollEvent> scrollEvents;
    private List<String> pasteEvents;
    private List<String> autofillDetected;
    private long timeSpent;
    private String currentPage;
    private LocalDateTime timestamp;
    private String userAgent;
    private String screenResolution;
    private String ipAddress;
    private Location location;
    private List<String> suspiciousActivity;

    public static class KeyPress {
        private String key;
        private long time;
        
        // getters and setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public long getTime() { return time; }
        public void setTime(long time) { this.time = time; }
    }

    public static class MouseClick {
        private int x;
        private int y;
        private long time;
        private String element;
        
        // getters and setters
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public long getTime() { return time; }
        public void setTime(long time) { this.time = time; }
        public String getElement() { return element; }
        public void setElement(String element) { this.element = element; }
    }

    public static class MouseHover {
        private int x;
        private int y;
        private long time;
        private String element;
        
        // getters and setters
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public long getTime() { return time; }
        public void setTime(long time) { this.time = time; }
        public String getElement() { return element; }
        public void setElement(String element) { this.element = element; }
    }

    public static class ScrollEvent {
        private double scrollY;
        private long time;
        
        // getters and setters
        public double getScrollY() { return scrollY; }
        public void setScrollY(double scrollY) { this.scrollY = scrollY; }
        public long getTime() { return time; }
        public void setTime(long time) { this.time = time; }
    }

    public static class Location {
        private double latitude;
        private double longitude;
        private double accuracy;
        private String locationName;
        private String error;
        
        // getters and setters
        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
        public double getAccuracy() { return accuracy; }
        public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
        public String getLocationName() { return locationName; }
        public void setLocationName(String locationName) { this.locationName = locationName; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }

    // Main class getters and setters
    public List<KeyPress> getKeysPressed() { return keysPressed; }
    public void setKeysPressed(List<KeyPress> keysPressed) { this.keysPressed = keysPressed; }
    public List<MouseClick> getMouseClicks() { return mouseClicks; }
    public void setMouseClicks(List<MouseClick> mouseClicks) { this.mouseClicks = mouseClicks; }
    public List<MouseHover> getMouseHovers() { return mouseHovers; }
    public void setMouseHovers(List<MouseHover> mouseHovers) { this.mouseHovers = mouseHovers; }
    public List<ScrollEvent> getScrollEvents() { return scrollEvents; }
    public void setScrollEvents(List<ScrollEvent> scrollEvents) { this.scrollEvents = scrollEvents; }
    public List<String> getPasteEvents() { return pasteEvents; }
    public void setPasteEvents(List<String> pasteEvents) { this.pasteEvents = pasteEvents; }
    public List<String> getAutofillDetected() { return autofillDetected; }
    public void setAutofillDetected(List<String> autofillDetected) { this.autofillDetected = autofillDetected; }
    public long getTimeSpent() { return timeSpent; }
    public void setTimeSpent(long timeSpent) { this.timeSpent = timeSpent; }
    public String getCurrentPage() { return currentPage; }
    public void setCurrentPage(String currentPage) { this.currentPage = currentPage; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getScreenResolution() { return screenResolution; }
    public void setScreenResolution(String screenResolution) { this.screenResolution = screenResolution; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public List<String> getSuspiciousActivity() { return suspiciousActivity; }
    public void setSuspiciousActivity(List<String> suspiciousActivity) { this.suspiciousActivity = suspiciousActivity; }
}
