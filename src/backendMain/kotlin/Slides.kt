import com.github.pambrose.slides.SlideDeck.Companion.slideDeck

val slides =
  slideDeck {

    println("Reading slides")

    slide("Season to Leave") {
      addText(
        """
            <h1>Welcome to this lesson</h1>
            ## I am a header 
            ### I am a smaller header 
            
            Here are some bullet points:
            * Item 1 with *italics*
            * Item 2 with **bold**
            
            When will you choose to leave?  
        """
      )

      addImage(
        "https://www.nps.gov/articles/000/images/Runaway-Slave-Advertisement-1_Columbus-Democrat-Columbus-MS-_18-August-1838_2.jpg",
        300,
        300
      )

      addText(
        """
        <br>
        This is more text
        <br>
        """
      )

      addImage(
        "https://www.nps.gov/articles/000/images/Runaway-Slave-Advertisement-1_Columbus-Democrat-Columbus-MS-_18-August-1838_2.jpg",
        400,
        400
      )


      verticalChoices()

      addChoice("Spring", "Spring Choice")
      addChoice("Summer", "Summer Choice")
      addChoice("Fall", "Fall Choice")
      addChoice("Winter", "Winter Choice")
    }

    slide("Spring Choice") {
      addText(
        """
        # This is the spring choice slide
        
        Here are the choices of clothing:
      """
      )

      horizontalChoices()

      addChoice("Clothing Choice 1", "Warm Jacket1")
      addChoice("Clothing Choice 2", "Warm Jacket2")
      addChoice("Clothing Choice 3", "Warm Jacket3")
    }

    slide("Summer Choice") {
      addText(
        """
        ## Summer Slide
        * this is more
        * this is also more
        """
      )
    }

    slide("Fall Choice") {
      addText("Fall Slide")
    }

    slide("Winter Choice") {
      addText("Winter Slide")
    }

    slide("Warm Jacket1") {
      addText("Description for Warm Jacket1")

      addChoice("Red Jacket", "Red Jacket")
      addChoice("Blue Jacket", "Blue Jacket")
    }

    slide("Red Jacket", true) {
      addText(
        """
        ## Success!
        Summer Slide
        """
      )
    }

    slide("Blue Jacket") {
      addText("Description for Blue Jacket")
    }

    slide("Warm Jacket2") {
      addText("Description for Warm Jacket2")
    }

    slide("Warm Jacket3") {
      addText("Description for Warm Jacket3")
    }
  }
