package com.github.pambrose

import com.github.pambrose.DbmsTxs.allUserSummaries
import com.github.pambrose.EndPoints.SLIDE
import com.github.pambrose.HistoryWalkServer.masterSlides
import kotlinx.html.*
import kotlinx.html.stream.createHTML

object Pages {
  fun displayUserSummary() =
    createHTML()
      .html {
        body {
          div {
            //style = "float:left;width:50%;"
            table {
              style = "border-collapse: separate; border-spacing: 10px 5px;"
              tr {
                th {
                  style = "text-align:left;"
                  +"Name"
                }
                th {
                  style = "text-align:left;"
                  +"Email"
                }
                th { +"Decisions" }
                th {
                  style = "text-align:left;"
                  +"Success"
                }
                th {
                  style = "text-align:left;"
                  +"Last Slide"
                }
              }
              allUserSummaries()
                .sortedBy { it.decisionCount }
                .forEach { summary ->
                  tr {
                    td { +summary.fullName }
                    td { +summary.email }
                    td {
                      style = "text-align:center;"
                      +summary.decisionCount.toString()
                    }
                    td {
                      +masterSlides.findSlideByPathName(summary.lastPathName).success.toString()
                    }
                    td { +summary.lastPathName }
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
                .forEach { (slideId, slides) ->
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
                      slides.forEachIndexed { i, _ ->
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