import kr.local.listenloop.SentenceSearch;
public class SearchCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  check(SentenceSearch.matches("I want to go."," WANT   TO "));
  check(!SentenceSearch.matches("I want tomorrow.","want to"));
  check(SentenceSearch.matches("I have to go.","have to"));
  check(SentenceSearch.matches("I don't know.","DON’T KNOW"));
  check(SentenceSearch.matches("알겠어. 잠시만.","잠시"));
  check(SentenceSearch.matches("Really?", "Really?"));
  check(!SentenceSearch.matches("Anything", ".*"));
  check(!SentenceSearch.matches("Anything", "  "));
  check(!SentenceSearch.matches("I wanted more", "want"));
  check(SentenceSearch.matches("I want\u00a0to go", "want to"));
  System.out.println("PASS: 10 search cases (phrases, boundaries, Korean, punctuation, literal regex, spaces)");
 }
}
