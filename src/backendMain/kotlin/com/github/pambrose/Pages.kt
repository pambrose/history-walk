package com.github.pambrose

import com.github.pambrose.ContentService.Companion.allUserSummaries
import com.github.pambrose.EndPoints.SLIDE
import com.github.pambrose.HistoryWalkServer.masterSlides
import kotlinx.html.*
import kotlinx.html.stream.createHTML

object Pages {
  fun displayUserSummary(uuid: String) =
    createHTML()
      .html {
        body {
          div {
            //style = "float:left;width:50%;"
            table {
              style = "border-collapse: separate; border-spacing: 10px 5px;"
              tr {
                th { +"Name" }
                th { +"Email" }
                th { +"Decisions" }
              }
              allUserSummaries(uuid)
                .sortedBy { it.decisionCount }
                .forEach { summary ->
                  tr {
                    td { +summary.fullName }
                    td { +summary.email }
                    td {
                      style = "text-align:center;"
                      +summary.decisionCount.toString()
                    }
                  }
                }
            }
          }
        }
      }


  fun displayAllSlides() =
    createHTML()
      .html {
        body {
          div {
            //style = "float:left;width:50%;"
            table {
              style = "width:100%;border-collapse: separate; border-spacing: 10px 5px;"
              tr {
                th { +"ID" }
                th { +"Title" }
                th { +"Instances" }
              }
              masterSlides.slideIdMap
                .toSortedMap()
                .filter { it.key != -1 }
                .forEach { slideId, slides ->
                  tr {
                    td {
                      style = "text-align:right;"
                      +"$slideId:"
                    }
                    td {
                      style = "width:25%;"
                      a { href = "/$SLIDE/$slideId"; +" ${slides[0].title}" }
                    }
                    td {
                      //style = "padding-right:15px;"
                      slides.forEachIndexed { i, slide ->
                        a { href = "/$SLIDE/$slideId/$i"; +" $i" }
                        +" "
                      }
                    }
                  }
                }
            }
          }
        }
      }
}