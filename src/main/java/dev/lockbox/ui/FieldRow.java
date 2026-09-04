package dev.lockbox.ui;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import dev.lockbox.vault.NewField;

interface FieldRow {

    HorizontalLayout layout();

    NewField toField();
}
