package com.github.pambrose

import io.kvision.form.FormPanel
import io.kvision.form.check.CheckBox
import io.kvision.form.formPanel
import io.kvision.form.text.Text
import io.kvision.form.text.TextInputType
import io.kvision.i18n.I18n.tr
import io.kvision.panel.StackPanel
import io.kvision.utils.px

object EditPanel : StackPanel() {

  private var editingId: Int? = null

  private val formPanel: FormPanel<Address>

  init {
    padding = 10.px

    formPanel = formPanel {
      add(Address::firstName, Text(label = "${tr("First name")}:").apply { maxlength = 255 })
      add(Address::lastName, Text(label = "${tr("Last name")}:").apply { maxlength = 255 })
      add(Address::email, Text(TextInputType.EMAIL, label = "${tr("E-mail")}:").apply { maxlength = 255 }) {
        it.getValue()
          ?.matches("(?:[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])")
      }
      add(Address::phone, Text(label = "${tr("Phone number")}:").apply { maxlength = 255 })
      add(Address::postalAddress, Text(label = "${tr("Postal address")}:").apply { maxlength = 255 })
      add(Address::favourite, CheckBox(label = tr("Mark as favourite")))
    }
    add(OldMainPanel)
  }

  fun add() {
    formPanel.clearData()
    open(null)
  }


  private fun open(editingId: Int?) {
    this.editingId = editingId
    activeChild = formPanel
    formPanel.validate()
    formPanel.getControl(Address::firstName)?.focus()
  }

  private fun close() {
    editingId = null
    activeChild = OldMainPanel
  }
}
