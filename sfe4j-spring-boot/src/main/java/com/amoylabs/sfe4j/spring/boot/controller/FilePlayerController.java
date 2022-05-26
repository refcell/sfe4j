package com.amoylabs.sfe4j.spring.boot.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FilePlayerController {

    @RequestMapping("/file-player")
    public void exec(@RequestParam("file") @Nullable String filePath, HttpServletResponse response) {
        try {
            File file = new File(filePath);
            String[] envp = new String[] { "path=" + System.getenv("path") };
            playVideo(file, envp, response);
        } catch (Exception e) {
            System.err.println(e.getLocalizedMessage());
        }
    }

    private void playVideo(File file, String[] envp, HttpServletResponse response) throws IOException {
        String name = "\"" + file.getName() + "\"";
        String command = null;
        if (file.getName().toLowerCase().endsWith(".mkv")) {
            int i;
            if ((i = getSubtitleIndex(file, name, envp)) > -1) {
                command = String.format("ffmpeg -loglevel error -i %s -f matroska -vf subtitles=%s:si=%s -", name, name, i);
            }
        }
        if (command == null) {
            command = String.format("ffmpeg -loglevel error -i %s -f matroska -", name);
        }
        Process process = Runtime.getRuntime().exec(command, envp, file.getParentFile());
        try {
            printErrLog(process);
            IOUtils.copy(process.getInputStream(), response.getOutputStream());
            response.flushBuffer();
        } finally {
            process.destroy();
        }
    }

    private void printErrLog(Process process) {
        new Thread(() -> {
            try (InputStream is = process.getErrorStream()) {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    System.err.println(line);
                }
            } catch (Exception e) {
                System.err.println(e.getLocalizedMessage());
            }
        }).start();
    }

    private int getSubtitleIndex(File file, String name, String[] envp) throws IOException {
        int index = -1;
        String command = String.format("ffprobe -loglevel error -select_streams s -show_entries stream=index:stream_tags=language -of csv=p=0 %s", name);
        Process process = Runtime.getRuntime().exec(command, envp, file.getParentFile());
        try (InputStream is = process.getInputStream()) {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                index++;
                if (line.endsWith(",chi")) {
                    return index;
                }
            }
            if (index > -1) {
                return 0;
            }
        } catch (Exception e) {
            System.err.println(e.getLocalizedMessage());
        } finally {
            process.destroy();
        }
        return index;
    }
}
