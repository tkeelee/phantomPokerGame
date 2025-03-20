package com.example.poker.model;

public class Card {
    private final String value;
    private final String suit;
    private final String rank;
    private final String color;

    public Card(String value, String suit,String rank, String color) {
        this.value = value;
        this.suit = suit;
        this.rank = rank;
        this.color = color;
    }

    public String getValue() {
        return value;
    }

    public String getSuit() {
        return suit;
    }

    public String getRank() {
        return rank;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return value + " of " + suit;
    }
}