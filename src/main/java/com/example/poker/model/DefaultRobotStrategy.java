package com.example.poker.model;

import java.util.*;

/**
 * 默认机器人策略实现
 */
public class DefaultRobotStrategy implements RobotStrategy {
    private static final Random random = new Random();
    private final String difficulty; // EASY, MEDIUM, HARD
    
    /**
     * 构造函数
     * @param difficulty 难度级别
     */
    public DefaultRobotStrategy(String difficulty) {
        this.difficulty = difficulty;
    }
    
    @Override
    public boolean decideToChallenge(String lastClaim, List<Card> currentPile, List<Card> hand) {
        if (lastClaim == null || currentPile == null || currentPile.isEmpty() || hand == null) {
            return false;
        }

        // 解析上一个声明
        String[] parts = lastClaim.split(" ");
        if (parts.length < 2) {
            return false;
        }
        
        int claimedCount;
        try {
            claimedCount = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        
        String claimedValue = parts[1];

        // 根据难度级别调整基础质疑概率
        double challengeProbability = switch (difficulty) {
            case "EASY" -> 0.2;
            case "MEDIUM" -> 0.4;
            case "HARD" -> 0.6;
            default -> 0.3;
        };

        // 计算手牌中相同点数的牌的数量
        int sameValueCount = countCardsByRank(hand, claimedValue);
        int handCount = hand.size();

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

    @Override
    public List<Card> selectCardsToPlay(String lastClaim, List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Card> selectedCards = new ArrayList<>();
        
        if (lastClaim == null) {
            // 如果是新的一轮，选择最优的牌
            String bestValue = findBestValueToPlay(hand);
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
            
            for (int i = 0; i < cardsToSelect && i < sameValueCards.size(); i++) {
                selectedCards.add(sameValueCards.get(i));
            }
        } else {
            // 如果不是新的一轮，需要根据上一个声明选择牌
            String[] parts = lastClaim.split(" ");
            if (parts.length < 2) {
                return selectedCards;
            }
            
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

    @Override
    public String generateClaim(List<Card> selectedCards, String lastClaim) {
        if (selectedCards == null || selectedCards.isEmpty()) {
            return null;
        }

        String valueToClaimStr;
        if (lastClaim != null) {
            // 如果不是新的一轮，必须声明相同的点数
            String[] parts = lastClaim.split(" ");
            if (parts.length < 2) {
                return null;
            }
            valueToClaimStr = parts[1];
        } else {
            // 如果是新的一轮，选择实际的牌值或者说谎
            Card firstCard = selectedCards.get(0);
            
            // 根据难度决定是否说谎
            double lieThreshold = switch (difficulty) {
                case "EASY" -> 0.1;    // 简单模式10%概率说谎
                case "MEDIUM" -> 0.3;   // 中等模式30%概率说谎
                case "HARD" -> 0.5;     // 困难模式50%概率说谎
                default -> 0.3;
            };
            
            if (random.nextDouble() < lieThreshold) {
                // 说谎，选择一个不同的牌值
                valueToClaimStr = findBestValueToLie(firstCard.getRank());
            } else {
                // 不说谎，使用实际牌值
                valueToClaimStr = firstCard.getRank();
            }
        }

        // 构建声明字符串
        return selectedCards.size() + " " + valueToClaimStr;
    }

    /**
     * 统计手牌中指定点数的牌的数量
     * @param hand 手牌
     * @param rank 点数
     * @return 数量
     */
    private int countCardsByRank(List<Card> hand, String rank) {
        if (hand == null || rank == null) {
            return 0;
        }
        
        int count = 0;
        for (Card card : hand) {
            if (card.getRank().equals(rank)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 找出最适合出牌的点数
     * @param hand 手牌
     * @return 最佳点数
     */
    private String findBestValueToPlay(List<Card> hand) {
        if (hand == null || hand.isEmpty()) {
            return "A"; // 默认值
        }
        
        // 统计每个点数的牌的数量
        Map<String, Integer> valueCount = new HashMap<>();
        for (Card card : hand) {
            String rank = card.getRank();
            valueCount.merge(rank, 1, Integer::sum);
        }
        
        // 根据难度选择策略
        if (difficulty.equals("HARD")) {
            // 困难模式优先选择数量最多的点数
            return valueCount.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(hand.get(0).getRank());
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
        return hand.get(random.nextInt(hand.size())).getRank();
    }

    /**
     * 找出最适合说谎的点数
     * @param actualValue 实际点数
     * @return 适合说谎的点数
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
} 