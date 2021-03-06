DiscordScanner
##example:
```Java
public static void main(String[] args) {
    DiscordApi api = new DiscordApiBuilder().setToken(args[0]).login().join(); //logs in

    User user = api.getOwner().join(); //gets the owner of the bot
    DiscordScanner scanner = new DiscordScanner(user.sendMessage("0! (Type '?' for help)").join(), user.getId());

    int i = 0;
    while (!scanner.isTerminated()) {
        while (scanner.nextIsReady()) { //checks if next is ready.
            String str = scanner.nextLine();
            if (str != null) { //makes sure str isn't null (just to makesure)
                switch (str.toLowerCase()) {
                    case "q":
                        scanner.terminate();
                        break;
                    case "+":
                        i++;
                        user.sendMessage(i + "").exceptionally(ExceptionLogger.get());
                        break;
                    case "-":
                        i--;
                        user.sendMessage(i + "").exceptionally(ExceptionLogger.get());
                        break;
                    case "?":
                        user.sendMessage("Commands:\n\t'?' -> Displays this message.\n\t'+' -> Adds one to the counter\n\t'-' -> Subtracts one from the counter\n\t'q' -> Quits this program").exceptionally(ExceptionLogger.get());
                        break;
                    default:
                        user.sendMessage("Command `" + str  + "` is not known. Type '?' for help").exceptionally(ExceptionLogger.get());
                }
            }
        }

        try {
            Thread.sleep(15L); //waits 10ms
        } catch (InterruptedException ignored) { }
    }
}
```
