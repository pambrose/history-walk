import com.github.pambrose.slides.SlideDeck.Companion.slideDeck

val slides =
  slideDeck {

    println("Reading slides")

    slide("Major Decision") {
      addText(
        """
## Major Decision

You suffer a horrific and vicious beating, worse than others in 
the past from your drunken, enraged owner that maims your face 
(a mangled and broken jaw).  While you once thought of him as 
family (your uncle) and that you were special, it is clear now 
that that is not the case.  You conclude that to stay much 
longer would mean death.  You are determined to escape sometime 
in the coming months.

Do you tell your intentions to your two best friends, fellow 
slaves on the neighboring plantation?         
      """
      )
      //       <img src="https://www.nps.gov/articles/000/images/Runaway-Slave-Advertisement-1_Columbus-Democrat-Columbus-MS-_18-August-1838_2.jpg" alt="Pic" width="300" height="400" style="border:5px solid black"/>
      addChoice("Yes, tell your two best friends", "Companion Decision")
      addChoice("No, do not tell your two best friends", "Do Not Tell Best Friends")
    }

    slide("Companion Decision") {
      addText(
        """
# Companion Decision

Eager to join you, your friend John Farney asks around and says that 
you should contact **first** (1) an ‘entrepreneurial’ (and maybe sympathetic?) 
white man named Ross –– who offers to ‘obtain’ and provide forged free 
papers and a pair of pistols (executable offenses for Ross) in exchange 
for $10 dollars cash (a lot at the time!), bacon, flour, and other staples 
and **second** (2) an elderly enslaved man named Ben, who is known for being 
very wise.

Will you risk beating, torture, or worse by stealing the items that Ross 
wants in exchange for what he offers?      
        """
      )

      addChoice("Yes, take your chances and go meet with Ross then meet with Ben", "Meet With Ross and Ben")
      addChoice("Yes, take your chances, but skip talking to Ben and just go to meet with Ross", "Meet With Just Ross")
      addChoice("No, skip Ross and just go to Old Ben", "Go To Old Ben")
      addChoice("No, play it safe and seek escape without talking to Ross or Ben", "Without Talking To Ross or Ben")
    }

    slide("Do Not Tell Best Friends", true) {
      addText(
        """
## Summer Slide
* this is more
* this is also more
        """
      )
    }

    val ross = """
## Ross Encounter
        
Having risked severe punishment, you have managed to steal some bacon, 
flour, bread, fruit preserves, and 12 dollars (two of which you will keep).  
In the past, you would not have dared to even think of stealing from your 
former master, who was also your father.  But having been sold, along with 
your mother and siblings, to one you thought of as an uncle and then to 
have been subjected to savage beatings, you feel no remorse about the deed.  

As instructed, you go to the dense thicket at the edge of town right near 
dusk, when the light is low and it would be difficult for someone to spot 
your meeting.  You call out, “Ross,” hoping that he’s there, fearing that 
you will get caught, and worried that he might cheat you.  He asks if you 
have what he wants, and when you nod and hold up a sack with the goods, 
he steps up close and offers a linen bundle in exchange.  

Do you complete the deal?
        """
    slide("Meet With Ross and Ben") {
      addText(ross)
    }

    slide("Meet With Just Ross") {
      addText(ross)
    }

    slide("Go To Old Ben") {
      addText(
        """
## Old Ben: When to Go

Old Ben, who has been on the plantation next door some time and is 
trusted by your partner, relays to him the advice that your chances 
to escape will be best at Christmas time. He argues that there are 
four good reasons:

1) Demands for field work are down, so you won’t be as missed.  
2) Owners, as well as patrollers, are distracted and less vigilant, 
with people interested in family gatherings and feasting.  
3) The colder weather also means the patrollers will not be outdoors as often.  
4) There will be the greatest cover of darkness with longer nights.  
(That said, if you decide upon leaving in winter, you will need to make 
sure to have ample cold weather clothing — you might have to steal some.)  

Sounds good, but then again, if he knew so much, why is he still here?

When will you leave?
 """
      )
      addChoice(
        "Leave in the summer, when the weather is warmest and you can easily sleep outdoors",
        "Summer Departure"
      )
      addChoice(
        "Leave in the autumn, hoping to slip away in the business and hub-bub of harvest time",
        "Autumn Departure"
      )
      addChoice(
        "Leave in the spring, when love is in the air",
        "Spring Departure"
      )
      addChoice(
        "Follow Old Ben’s advice and leave in the winter, right at Christmas, when it is coldest and darkest",
        "Winter Departure"
      )

    }

    slide("Without Talking To Ross or Ben") {
      addText(
        """
        ## Summer Slide
        * this is more
        * this is also more
        """
      )
    }

    slide("Summer Departure") {
      addText(
        """
        ## Summer Departure
        * this is more
        * this is also more
        """
      )
    }

    slide("Autumn Departure") {
      addText(
        """
        ## Autumn Departure
        * this is more
        * this is also more
        """
      )
    }

    slide("Spring Departure") {
      addText(
        """
        ## Spring Departure
        * this is more
        * this is also more
        """
      )
    }

    slide("Winter Departure") {
      addText(
        """
        ## Winter Departure
        * this is more
        * this is also more
        """
      )
    }
  }
