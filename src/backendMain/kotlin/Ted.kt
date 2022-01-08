import com.github.pambrose.slides.SlideDeck.Companion.slideDeck

val introductionPage = """ 
    <div style="text-align:center;">
    <div style="padding-top:10px;">
    <font size="+3">
    Introduction 
    </font>
    </div>
    <div style="text-align:left;padding-top:30px;">
    <font size="+1">
    Your name is Moses. Your mother, Nancy was an Enslaved African, but your 
    father was named Henry, a white slave owner. A few years after you were 
    born Henry’s wife grew very suspicious that her husband was your father.
    After some investigative work, it was revealed to be true. In a short 
    amount of time you and your mother were sold to a cruel slave owner name 
    Mr. Gooch. He seemed to revel in punishing those he owned. Your days 
    were filled with hard toil in the fields and the fear of physical 
    punishment. You began to contemplate whether it was time to runaway.
    If you are caught the punishment will be severe, but there is a chance of freedom
    </font>
    </div>
    </div> 
"""

val errorText = "Incorrect. Try again."

val page1Text = """
  You are on the way home from a exhausting day of harvesting rice, when a 
  friend motions for you to come over and chat. He has heard that you are 
  in line to be punished for not taking proper care of tools. You cannot 
  stand the thought of suffering another lashing. But maybe the rumor isn’t 
  true and you are in the clear.
  
  Options:
"""

val page2Text = """
  
"""

val page3Text = """
  
"""

val page4Text = """
  
"""

val page5Text = """
  
"""

val page6Text = """
  
"""

val page7Text = """
  
"""

val page8Text = """
  
"""

val slides2 =
  slideDeck {

    val errorSlide = slide(99, "Incorrect Answer", errorText, displayTitle = false) {}

    val slide3 =
      slide(3, "2nd Decision", page2Text, success = true) {

      }

    slide(1, "Introduction", introductionPage, root = true) {
      choice(
        "Continue",
        slide(2, "1st Decision", page1Text) {
          choice(
            "Head back to the slave quarters and take your chances the rumor was false",
            errorSlide.copyOf("Incorrect1")
          )
          choice("Run away right now", slide3)
          choice("Wait until the night to escape", errorSlide.copyOf("Incorrect2"))
        },
      )
    }
  }
