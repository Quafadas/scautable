
# SCala AUto TABLE
Auto-magically generate html tables from case classes

## Elevator Pitch 



## Infrequently Asked Questions

### Can I help
If you know why this line doesn't work; 
https://github.com/Quafadas/scautable/blob/c59cffc07391d47f4a0b2be778e6d9795e2e4feb/src/main/scala/scautable.scala#L133

I'd love to know... 

### Is this project a good idea
Unclear. But I wanted to play with Mirrors.

### How does it work
According to Arthur C. Clarke, any sufficiently advanced technology is indistinguisgable from magic. To me: this is clearly magic. 

Source: I aggressively copy pasted everything from here and poked it with a sharp stick until it did what I wanted.
https://blog.philipp-martini.de/blog/magic-mirror-scala3/

### Limitations
See tests; 
- formatting is not compoundable - "flat" case classes only.
- Formatting is implied by the type. To format your own types, you'll need to write a given for it.
- As I don't _really_ understand how it works, it's unlikely to get extended further... 
- Extending it further is probably a really bad idea anyway