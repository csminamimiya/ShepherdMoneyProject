package com.shepherdmoney.interviewproject.controller;

import com.shepherdmoney.interviewproject.model.BalanceHistory;
import com.shepherdmoney.interviewproject.model.CreditCard;
import com.shepherdmoney.interviewproject.model.User;
import com.shepherdmoney.interviewproject.repository.CreditCardRepository;
import com.shepherdmoney.interviewproject.repository.UserRepository;
import com.shepherdmoney.interviewproject.vo.request.AddCreditCardToUserPayload;
import com.shepherdmoney.interviewproject.vo.request.UpdateBalancePayload;
import com.shepherdmoney.interviewproject.vo.response.CreditCardView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class CreditCardController {
    @Autowired
    private CreditCardRepository creditCardRepository;
    @Autowired
    private UserRepository userRepository;
    // TODO: wire in CreditCard repository here (~1 line)

    @PostMapping("/credit-card")
    public ResponseEntity<Integer> addCreditCardToUser(@RequestBody AddCreditCardToUserPayload payload) {
        // Check if the user exists
        Optional<User> userOptional = userRepository.findById(payload.getUserId());
        if (!userOptional.isPresent()) {
            // Return 400 Bad Request if the user does not exist. Use an arbitrary negative number or a specific code as the error indicator.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(-1);
        }

        // Create a new credit card
        CreditCard newCard = new CreditCard();
        newCard.setNumber(payload.getCardNumber());  // Assume card number can be any format/length
        newCard.setUser(userOptional.get());  // Associate the card with the found user

        // Save the credit card to the database
        CreditCard savedCard = creditCardRepository.save(newCard);

        // Return the ID of the newly created credit card in a 200 OK response
        return ResponseEntity.ok(savedCard.getId());
        // TODO: Create a credit card entity, and then associate that credit card with user with given userId
        //       Return 200 OK with the credit card id if the user exists and credit card is successfully associated with the user
        //       Return other appropriate response code for other exception cases
        //       Do not worry about validating the card number, assume card number could be any arbitrary format and length
    }

    @GetMapping("/credit-card:all")
    public ResponseEntity<List<CreditCardView>> getAllCardOfUser(@RequestParam int userId) {
        List<CreditCard> cards = creditCardRepository.findAllByUserId(userId);
        List<CreditCardView> cardViews = new ArrayList<>();
        if (cards.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        for (CreditCard card : cards) {
            CreditCardView view = new CreditCardView(card.getIssuanceBank(), card.getNumber());
            cardViews.add(view);
        }
        return ResponseEntity.ok(cardViews);
        // TODO: return a list of all credit card associated with the given userId, using CreditCardView class
        //       if the user has no credit card, return empty list, never return null
    }

    @GetMapping("/credit-card:user-id")
    public ResponseEntity<Integer> getUserIdForCreditCard(@RequestParam String creditCardNumber) {
        // TODO: Given a credit card number, efficiently find whether there is a user associated with the credit card
        //       If so, return the user id in a 200 OK response. If no such user exists, return 400 Bad Request
        Optional<CreditCard> cardOptional = creditCardRepository.findByNumber(creditCardNumber);
        if (cardOptional.isPresent()) {
            User user = cardOptional.get().getUser();
            if (user != null) {
                return ResponseEntity.ok(user.getId());
            }
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/credit-card:update-balance")
    public ResponseEntity<String> postMethodName(@RequestBody UpdateBalancePayload[] payload) {
        for (UpdateBalancePayload p : payload) {
            // Find the credit card by number
            Optional<CreditCard> cardOptional = creditCardRepository.findByNumber(p.getCardNumber());
            if (!cardOptional.isPresent()) {
                return ResponseEntity.badRequest().body("Card not found for number: " + p.getCardNumber());
            }
            CreditCard card = cardOptional.get();

            // Retrieve and sort the balance history
            List<BalanceHistory> histories = new ArrayList<>(card.getBalanceHistories());
            Collections.sort(histories, Comparator.comparing(BalanceHistory::getDate));

            // Update balance history based on payload
            updateBalanceHistories(histories, p);

            // Save the updated card
            creditCardRepository.save(card);
        }
        return ResponseEntity.ok("Balance updated successfully");
        //TODO: Given a list of transactions, update credit cards' balance history.
        //      1. For the balance history in the credit card
        //      2. If there are gaps between two balance dates, fill the empty date with the balance of the previous date
        //      3. Given the payload `payload`, calculate the balance different between the payload and the actual balance stored in the database
        //      4. If the different is not 0, update all the following budget with the difference
        //      For example: if today is 4/12, a credit card's balanceHistory is [{date: 4/12, balance: 110}, {date: 4/10, balance: 100}],
        //      Given a balance amount of {date: 4/11, amount: 110}, the new balanceHistory is
        //      [{date: 4/12, balance: 120}, {date: 4/11, balance: 110}, {date: 4/10, balance: 100}]
        //      Return 200 OK if update is done and successful, 400 Bad Request if the given card number
        //        is not associated with a card.
    }
    private void updateBalanceHistories(List<BalanceHistory> histories, UpdateBalancePayload payload) {
        LocalDate payloadDate = payload.getDate();
        double payloadAmount = payload.getAmount();
        boolean dateFound = false;

        // Iterate through existing histories to find the correct position for the new data
        for (int i = 0; i < histories.size(); i++) {
            BalanceHistory current = histories.get(i);
            if (!dateFound && current.getDate().isBefore(payloadDate)) {
                continue;
            }
            if (current.getDate().equals(payloadDate)) {
                double difference = payloadAmount - current.getBalance();
                adjustSubsequentBalances(histories, i, difference);
                current.setBalance(payloadAmount);
                dateFound = true;
            } else if (current.getDate().isAfter(payloadDate)) {
                // Insert new balance history if date not found
                BalanceHistory newHistory = new BalanceHistory();
                newHistory.setDate(payloadDate);
                newHistory.setBalance(payloadAmount);
                histories.add(i, newHistory);
                adjustSubsequentBalances(histories, i + 1, payloadAmount - current.getBalance());
                dateFound = true;
            }
        }
        // If the payload date is later than all existing dates, add it at the end
        if (!dateFound) {
            BalanceHistory newHistory = new BalanceHistory();
            newHistory.setDate(payloadDate);
            newHistory.setBalance(payloadAmount);
            histories.add(newHistory);
        }
    }

    private void adjustSubsequentBalances(List<BalanceHistory> histories, int startIndex, double difference) {
        for (int i = startIndex; i < histories.size(); i++) {
            BalanceHistory history = histories.get(i);
            history.setBalance(history.getBalance() + difference);
        }
    }
}
