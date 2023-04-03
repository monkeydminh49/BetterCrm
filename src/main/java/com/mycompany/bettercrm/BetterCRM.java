package com.mycompany.bettercrm;

import Controller.RequestAPI;
import Model.ClassRoom;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.raven.main.Main;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import javax.swing.SwingUtilities;

public class BetterCRM {

    public static Main main = new Main();

    public static void main(String[] args) throws IOException, URISyntaxException, ClassNotFoundException {
//        RequestAPI.getInstance().run();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    RequestAPI.getInstance().run();
                } catch (IOException | URISyntaxException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }

                main.setVisible(true);
//                new Main().setVisible(true);
            }
        });
    }
}