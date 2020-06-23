package org.logevents.jmx;

public interface StatisticsMXBean {

    int getMessagesLast5Minutes();

    int getMessagesLastHour();

    int getMessagesLastDay();
}
