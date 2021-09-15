package com.github.pambrose

import io.kvision.core.onEvent
import io.kvision.i18n.I18n.tr
import io.kvision.panel.SimplePanel
import io.kvision.table.HeaderCell
import io.kvision.table.Table
import io.kvision.table.TableType
import io.kvision.utils.px

object ListPanel : SimplePanel() {

  init {
    padding = 5.px

    val table = Table(types = setOf(TableType.STRIPED, TableType.HOVER)) {
      addHeaderCell(this@ListPanel.sortingHeaderCell(tr("First name"), Sort.FN))
      addHeaderCell(this@ListPanel.sortingHeaderCell(tr("Last name"), Sort.LN))
      addHeaderCell(this@ListPanel.sortingHeaderCell(tr("E-mail"), Sort.E))
      addHeaderCell(this@ListPanel.sortingHeaderCell("", Sort.F))
      addHeaderCell(HeaderCell(""))
    }
  }

  private fun sortingHeaderCell(title: String, sort: Sort) = HeaderCell(title) {
    onEvent {
      click = {
      }
    }
  }
}
