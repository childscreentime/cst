package io.github.childscreentime.model;

public class Credit {
    public long fiveExtends;
    public long minutes;
    public long tenExtends;

    public Credit(long minutes, long fiveExtends, long tenExtends) {
        this.minutes = minutes;
        this.fiveExtends = fiveExtends;
        this.tenExtends = tenExtends;
    }

    public static Credit fromString(String string) {
        String[] split = string.split(",");
        return new Credit(Long.parseLong(split[0]), Long.parseLong(split[1]), Long.parseLong(split[2]));
    }

    public void extend(long extendMinutes) {
        this.minutes += extendMinutes;
    }

    public boolean expiresSoon(long duration) {
        return 5 + duration >= this.minutes;
    }

    public String toString() {
        return String.format("Credit(minutes %s, fiveExtends %s, tenExtends %s)", new Object[]{Long.valueOf(this.minutes), Long.valueOf(this.fiveExtends), Long.valueOf(this.tenExtends)});
    }

    public String asString() {
        return String.format("%s,%s,%s", new Object[]{Long.valueOf(this.minutes), Long.valueOf(this.fiveExtends), Long.valueOf(this.tenExtends)});
    }

    public Credit getCreditNoMoreThen(long minutes2) {
        if (minutes2 >= this.minutes) {
            return this;
        }
        return new Credit(minutes2 > 0 ? minutes2 : 0, 0, 0);
    }
}
