package wintervillage.cards;

import javafx.scene.paint.Color;

import java.util.List;

public enum CardType {

    // ACTIVATION = Chance deck (机会卡)
    ACTIVATION("activation", "机会", "机会卡", Color.web("#E8871E"),
            List.of(
                    new CardDefinition("advance_illinois",     "Advance to Illinois",    "Advance to Illinois Ave (24)",                    0,  24, false),
                    new CardDefinition("advance_st_charles",   "Advance to St. Charles", "Advance to St. Charles Place (11)",               0,  11, false),
                    new CardDefinition("advance_reading_rr",   "Take a Trip to Reading", "Take a trip to Reading Railroad (5)",             0,   5, false),
                    new CardDefinition("advance_boardwalk",    "Advance to Boardwalk",   "Advance to Boardwalk (39)",                       0,  39, false),
                    new CardDefinition("advance_to_go",        "Advance to GO",          "Advance to GO (collect $200)",                    0,   0, false),
                    new CardDefinition("go_back_three",        "Go Back 3 Spaces",       "Go back 3 spaces",                                0,  -3, false),
                    new CardDefinition("bank_dividend",        "Bank Dividend",          "Bank pays you dividend of $50",                  50,  -1, false),
                    new CardDefinition("pay_poor_tax",         "Pay Poor Tax",           "Pay poor tax of $15",                           -15,  -1, false),
                    new CardDefinition("building_loan",        "Building Loan Matures",  "Your building loan matures — collect $150",     150,  -1, false),
                    new CardDefinition("crossword",            "Crossword Competition",  "You have won a crossword — collect $100",       100,  -1, false),
                    new CardDefinition("go_to_jail",           "Go to Jail",             "Go directly to Jail. Do not pass GO.",            0,  -1, true),
                    new CardDefinition("get_out_of_jail",      "Get Out of Jail Free",   "Keep this card until needed.",                    0,  -1, false),
                    new CardDefinition("street_repairs",       "Street Repairs",         "Street repairs — pay $40",                      -40,  -1, false),
                    new CardDefinition("beauty_contest",       "Beauty Contest",         "You have won second prize — collect $10",        10,  -1, false),
                    new CardDefinition("speeding_fine",        "Speeding Fine",          "Pay speeding fine of $15",                      -15,  -1, false)
            )),

    // RESOURCE = Community Chest deck (社区福利卡)
    RESOURCE("resource", "社区福利", "社区福利卡", Color.web("#2E86C1"),
            List.of(
                    new CardDefinition("advance_go_cc",        "Advance to GO",          "Advance to GO (collect $200)",                    0,   0, false),
                    new CardDefinition("bank_error",           "Bank Error",             "Bank error in your favour — collect $200",      200,  -1, false),
                    new CardDefinition("doctor_fee",           "Doctor's Fee",           "Doctor's fee — pay $50",                        -50,  -1, false),
                    new CardDefinition("sale_of_stock",        "Sale of Stock",          "From sale of stock you get $50",                 50,  -1, false),
                    new CardDefinition("holiday_fund",         "Holiday Fund Matures",   "Holiday fund matures — receive $100",           100,  -1, false),
                    new CardDefinition("income_tax_refund",    "Income Tax Refund",      "Income tax refund — collect $20",                20,  -1, false),
                    new CardDefinition("birthday",             "It's Your Birthday",     "It is your birthday — collect $10",              10,  -1, false),
                    new CardDefinition("life_insurance",       "Life Insurance",         "Life insurance matures — collect $100",         100,  -1, false),
                    new CardDefinition("hospital_fee",         "Hospital Fee",           "Pay hospital fees of $100",                    -100,  -1, false),
                    new CardDefinition("school_fee",           "School Fee",             "Pay school fees of $50",                        -50,  -1, false),
                    new CardDefinition("consultancy_fee",      "Consultancy Fee",        "Receive $25 consultancy fee",                    25,  -1, false),
                    new CardDefinition("street_repairs_cc",    "Street Repairs",         "Street repairs — pay $40",                      -40,  -1, false),
                    new CardDefinition("beauty_contest_cc",    "Beauty Contest",         "Second prize — collect $10",                     10,  -1, false),
                    new CardDefinition("inheritance",          "Inheritance",            "You inherit $100",                              100,  -1, false),
                    new CardDefinition("insurance_matures",    "Insurance Matures",      "Life insurance matures — collect $100",         100,  -1, false),
                    new CardDefinition("go_to_jail_cc",        "Go to Jail",             "Go directly to Jail. Do not pass GO.",            0,  -1, true)
            ));

    private final String id;
    private final String deckLabel;
    private final String displayName;
    private final Color accent;
    private final List<CardDefinition> cards;

    CardType(String id, String deckLabel, String displayName,
             Color accent, List<CardDefinition> cards) {
        this.id = id;
        this.deckLabel = deckLabel;
        this.displayName = displayName;
        this.accent = accent;
        this.cards = cards;
    }

    public String id() { return id; }
    public String deckLabel() { return deckLabel; }
    public String displayName() { return displayName; }
    public Color accent() { return accent; }
    public List<CardDefinition> cards() { return cards; }
}