package dev.lockbox.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("")
@PageTitle("Lockbox")
public class HomeView extends VerticalLayout {

    public HomeView() {
        add(new H1("Lockbox"));
        add(new Paragraph("Self hosted encrypted vault"));
    }
}
