package com.example.poker.repository;

import com.example.poker.model.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, String> {
}