package sortx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import sortx.ui.MainUI;

public class AppLauncher extends Application {
    private ConfigurableApplicationContext context;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        context = new SpringApplicationBuilder(SortXSpringBoot.class).run();
    }

    @Override
    public void start(Stage stage) {
        MainUI mainUI = context.getBean(MainUI.class);
        Scene scene = new Scene(mainUI.getRoot(), 1200, 750);
        stage.setTitle("sortX — Ordenação Genérica (Spring Boot + JavaFX)");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        context.close();
    }
}