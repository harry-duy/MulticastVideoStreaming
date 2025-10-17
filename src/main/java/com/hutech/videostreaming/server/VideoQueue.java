package com.hutech.videostreaming.server;

import java.io.File;
import java.util.*;

public class VideoQueue {
    private Queue<File> queue;
    private File currentVideo;

    public VideoQueue() {
        queue = new LinkedList<>();
    }

    public void addVideo(File video) {
        queue.offer(video);
    }

    public void addVideos(List<File> videos) {
        queue.addAll(videos);
    }

    public File getNext() {
        currentVideo = queue.poll();
        return currentVideo;
    }

    public File getCurrent() {
        return currentVideo;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public List<File> getQueueList() {
        return new ArrayList<>(queue);
    }

    public void clear() {
        queue.clear();
    }

    public boolean hasNext() {
        return !queue.isEmpty();
    }
}