package temp.kamon


object PercentEncoder {

  def encode(originalString: String): String = {
    val encodedString = new StringBuilder()

    for (character ← originalString) {
      if (shouldEncode(character)) {
        encodedString.append('%')
        val charHexValue = Integer.toHexString(character).toUpperCase
        if (charHexValue.length < 2)
          encodedString.append('0')

        encodedString.append(charHexValue)

      } else {
        encodedString.append(character)
      }
    }
    encodedString.toString()
  }

  def shouldEncode(ch: Char): Boolean = {
    if (ch > 128 || ch < 0) true
    else " %$&+,./:;=?@<>#%".indexOf(ch) >= 0;
  }
}
