import com.github.pambrose.slides.SlideDeck.Companion.slideDeck

val majorDecision = """
      ## Major Decision
      
      You suffer a horrific and vicious beating, worse than others in 
      the past from your drunken, enraged owner that maims your face 
      (a mangled and broken jaw).  While you once thought of him as 
      family (your uncle) and that you were special, it is clear now 
      that that is not the case. You conclude that to stay much 
      longer would mean death. You are determined to escape sometime 
      in the coming months.
      
      Do you tell your intentions to your two best friends, fellow 
      slaves on the neighboring plantation?         
      """

val companionDecision = """
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

val rossEncounter = """
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

val oldBenWhenToGo = """
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

// <img src="https://www.nps.gov/articles/000/images/Runaway-Slave-Advertisement-1_Columbus-Democrat-Columbus-MS-_18-August-1838_2.jpg" alt="Pic" width="300" height="400" style="border:5px solid black"/>

val slides =
  slideDeck {

    slide("Major Decision", majorDecision) {
      choice(
        "Yes, tell your two best friends",
        slide("Companion Decision", companionDecision) {
          choice("Yes, take your chances and go meet with Ross then meet with Ben",
            slide("Meet With Ross and Ben", rossEncounter) {
              choice(
                "Yes",
                slide("Ross Advice")
              )
              choice(
                "No",
                slide("Old Ben Via Ross")
              )
            }
          )

          choice(
            "Yes, take your chances, but skip talking to Ben and just go to meet with Ross",
            slide("Meet With Just Ross", rossEncounter) {
            }
          )

          choice(
            "No, skip Ross and just go to Old Ben",
            slide("Go To Old Ben", oldBenWhenToGo) {
              choice(
                "Leave in the summer, when the weather is warmest and you can easily sleep outdoors",
                slide("Summer Departure") {
                }
              )

              choice(
                "Leave in the autumn, hoping to slip away in the business and hub-bub of harvest time",
                slide("Autumn Departure")
              )
              choice(
                "Leave in the spring, when love is in the air",
                slide("Spring Departure")
              )
              choice(
                "Follow Old Ben’s advice and leave in the winter, right at Christmas, when it is coldest and darkest",
                slide("Winter Departure")
              )

            }
          )

          choice(
            "No, play it safe and seek escape without talking to Ross or Ben",
            slide("Without Talking To Ross or Ben")
          )
        })

      choice(
        "No, do not tell your two best friends",
        slide("Do Not Tell Best Friends", success = true)
      )
    }
  }
