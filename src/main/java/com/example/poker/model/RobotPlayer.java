package com.example.poker.model;

import java.util.*;

public class RobotPlayer extends Player {
    private static final Random random = new Random();
    private String difficulty; // EASY, MEDIUM, HARD
    private boolean isRobot = true;
    private int handCount; // 记录手牌数量
    private Map<String, Integer> cardCountMap; // 记录每种牌的数量

    public RobotPlayer(String id, String name, String difficulty) {
        super(id, name);
        this.difficulty = difficulty;
        this.cardCountMap = new HashMap<>();
        updateCardCount();
    }

    public boolean isRobot() {
        return isRobot;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * 更新手牌统计信息
     */
    private void updateCardCount() {
        cardCountMap.clear();
        List<Card> hand = getHand();
        handCount = hand.size();
        
        for (Card card : hand) {
            String rank = card.getRank();
            cardCountMap.merge(rank, 1, Integer::sum);
        }
    }

    /**
     * 机器人决定是否质疑上一个玩家
     * @param lastClaim 上一个玩家的声明
     * @param currentPile 当前牌堆
     * @return 是否质疑
     */
    public boolean decideToChallenge(String lastClaim, List<Card> currentPile) {
        if (lastClaim == null || currentPile.isEmpty()) {
            return false;
        }

        // 解析上一个声明
        String[] parts = lastClaim.split(" ");
        int claimedCount = Integer.parseInt(parts[0]);
        String claimedValue = parts[1];

        // 根据难度级别调整基础质疑概率
        double challengeProbability = switch (difficulty) {
            case "EASY" -> 0.2;
            case "MEDIUM" -> 0.4;
            case "HARD" -> 0.6;
            default -> 0.3;
        };

        // 计算手牌中相同点数的牌的数量
        int sameValueCount = cardCountMap.getOrDefault(claimedValue, 0);

        // 根据手牌情况调整质疑概率
        if (handCount <= 3) {
            // 手牌很少时，更倾向于质疑
            challengeProbability += 0.2;
        }

        // 如果声明的数量看起来不合理，增加质疑概率
        if (claimedCount > 4 || (claimedCount + sameValueCount > 4)) {
            challengeProbability += 0.3;
        }

        // 在困难模式下，根据历史记录调整概率
        if (difficulty.equals("HARD")) {
            // 如果手牌中完全没有这种牌，增加质疑概率
            if (sameValueCount == 0) {
                challengeProbability += 0.2;
            }
            // 如果手牌中这种牌的数量接近4张，增加质疑概率
            if (sameValueCount >= 3) {
                challengeProbability += 0.15;
            }
        }

        return random.nextDouble() < challengeProbability;
    }

    /**
     * 机器人选择要打出的牌
     * @param lastClaim 上一个玩家的声明
     * @return 选择的牌列表
     */
    public List<Card> selectCardsToPlay(String lastClaim) {
        List<Card> selectedCards = new ArrayList<>();
        List<Card> hand = getHand();
        
        if (lastClaim == null || hand.isEmpty()) {
            // 如果是新的一轮，选择最优的牌
            String bestValue = findBestValueToPlay();
            List<Card> sameValueCards = hand.stream()
                    .filter(card -> card.getRank().equals(bestValue))
                    .toList();
            
            // 根据难度决定出牌数量
            int cardsToSelect = switch (difficulty) {
                case "EASY" -> Math.min(1 + random.nextInt(2), sameValueCards.size());
                case "MEDIUM" -> Math.min(1 + random.nextInt(3), sameValueCards.size());
                case "HARD" -> Math.min(2 + random.nextInt(2), sameValueCards.size());
                default -> Math.min(1 + random.nextInt(2), sameValueCards.size());
            };
            
            for (int i = 0; i < cardsToSelect; i++) {
                selectedCards.add(sameValueCards.get(i));
            }
        } else {
            // 如果不是新的一轮，需要根据上一个声明选择牌
            String[] parts = lastClaim.split(" ");
            String claimedValue = parts[1];
            
            // 找出手牌中点数相同的牌
            List<Card> sameValueCards = hand.stream()
                    .filter(card -> card.getRank().equals(claimedValue))
                    .toList();
            
            if (sameValueCards.isEmpty()) {
                // 如果没有相同点数的牌，根据难度决定是否说谎
                double lieThreshold = switch (difficulty) {
                    case "EASY" -> 0.3;    // 简单模式30%概率说谎
                    case "MEDIUM" -> 0.5;   // 中等模式50%概率说谎
                    case "HARD" -> 0.7;     // 困难模式70%概率说谎
                    default -> 0.5;
                };
                
                if (random.nextDouble() < lieThreshold) {
                    // 选择说谎，随机选择1-2张牌
                    int cardsToSelect = 1 + random.nextInt(2);
                    for (int i = 0; i < Math.min(cardsToSelect, hand.size()); i++) {
                        selectedCards.add(hand.get(i));
                    }
                }
                // 如果不说谎，返回空列表表示过牌
            } else {
                // 如果有相同点数的牌，根据难度决定出牌数量
                int maxCards = switch (difficulty) {
                    case "EASY" -> 1;      // 简单模式最多出1张
                    case "MEDIUM" -> 2;     // 中等模式最多出2张
                    case "HARD" -> 3;       // 困难模式最多出3张
                    default -> 2;
                };
                
                int cardsToSelect = Math.min(maxCards, sameValueCards.size());
                for (int i = 0; i < cardsToSelect; i++) {
                    selectedCards.add(sameValueCards.get(i));
                }
            }
        }
        
        return selectedCards;
    }

    /**
     * 找出最适合出牌的点数
     */
    private String findBestValueToPlay() {
        // 统计每个点数的牌的数量
        Map<String, Integer> valueCount = new HashMap<>();
        for (Card card : getHand()) {
            valueCount.merge(card.getRank(), 1, Integer::sum);
        }
        
        // 根据难度选择策略
        if (difficulty.equals("HARD")) {
            // 困难模式优先选择数量最多的点数
            return valueCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(getHand().get(0).getRank());
        } else if (difficulty.equals("MEDIUM")) {
            // 中等模式随机选择数量较多的点数
            List<String> candidates = valueCount.entrySet().stream()
                    .filter(e -> e.getValue() >= 2)
                    .map(Map.Entry::getKey)
                    .toList();
            if (!candidates.isEmpty()) {
                return candidates.get(random.nextInt(candidates.size()));
            }
        }
        
        // 简单模式或其他情况随机选择
        return getHand().get(random.nextInt(getHand().size())).getRank();
    }

    /**
     * 找出最适合说谎的点数
     */
    private String findBestValueToLie(String actualValue) {
        List<String> allValues = Arrays.asList("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
        List<String> possibleValues = new ArrayList<>(allValues);
        possibleValues.remove(actualValue);
        
        if (difficulty.equals("HARD")) {
            // 困难模式倾向于选择接近实际点数的值
            int actualIndex = allValues.indexOf(actualValue);
            List<String> nearbyValues = new ArrayList<>();
            
            // 选择距离实际点数2以内的值
            for (String value : possibleValues) {
                int index = allValues.indexOf(value);
                if (Math.abs(index - actualIndex) <= 2) {
                    nearbyValues.add(value);
                }
            }
            
            if (!nearbyValues.isEmpty()) {
                return nearbyValues.get(random.nextInt(nearbyValues.size()));
            }
        }
        
        // 其他情况随机选择
        return possibleValues.get(random.nextInt(possibleValues.size()));
    }

    /**
     * 机器人生成声明
     * @param selectedCards 选择的牌
     * @param lastClaim 上一个玩家的声明
     * @return 声明内容
     */
    public String generateClaim(List<Card> selectedCards, String lastClaim) {
        if (selectedCards.isEmpty()) {
            return null;
        }

        String valueToClaimStr;
        if (lastClaim != null) {
            // 如果不是新的一轮，必须声明相同的点数
            String[] parts = lastClaim.split(" ");
            valueToClaimStr = parts[1];
        } else {
            // 如果是新的一轮，根据难度选择声明策略
            if (difficulty.equals("HARD")) {
                // 困难模式下，70%的概率说实话
                if (random.nextDouble() < 0.7) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    // 30%的概率说谎，选择手牌中数量较多的牌
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            } else if (difficulty.equals("MEDIUM")) {
                // 中等模式下，50%的概率说实话
                if (random.nextDouble() < 0.5) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            } else {
                // 简单模式下，30%的概率说实话
                if (random.nextDouble() < 0.3) {
                    valueToClaimStr = selectedCards.get(0).getRank();
                } else {
                    valueToClaimStr = findBestValueToLie(selectedCards.get(0).getRank());
                }
            }
        }

        return selectedCards.size() + " " + valueToClaimStr;
    }

    @Override
    public void setHand(List<Card> hand) {
        super.setHand(hand);
        updateCardCount();
    }
} 