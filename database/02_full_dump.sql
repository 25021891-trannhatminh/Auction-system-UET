CREATE DATABASE  IF NOT EXISTS `auctionsystem` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `auctionsystem`;
-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: auctionsystem
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `auctions`
--

DROP TABLE IF EXISTS `auctions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auctions` (
                            `auction_id` int NOT NULL AUTO_INCREMENT,
                            `item_id` int NOT NULL,
                            `seller_id` int NOT NULL,
                            `start_time` datetime NOT NULL,
                            `end_time` datetime NOT NULL,
                            `last_bid_time` timestamp NULL DEFAULT NULL,
                            `min_bid_increment` decimal(15,2) NOT NULL DEFAULT '0.00',
                            `reserve_price` decimal(15,2) DEFAULT NULL,
                            `snipe_window_seconds` smallint NOT NULL DEFAULT '300',
                            `snipe_extension_seconds` smallint NOT NULL DEFAULT '60',
                            `current_price` decimal(15,2) NOT NULL,
                            `current_winner_id` int DEFAULT NULL,
                            `status` enum('OPEN','RUNNING','FINISHED','PAID','CANCELED') NOT NULL DEFAULT 'OPEN',
                            `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`auction_id`),
                            UNIQUE KEY `item_id` (`item_id`),
                            KEY `seller_id` (`seller_id`),
                            KEY `current_winner_id` (`current_winner_id`),
                            CONSTRAINT `auctions_ibfk_1` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE,
                            CONSTRAINT `auctions_ibfk_2` FOREIGN KEY (`seller_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT,
                            CONSTRAINT `auctions_ibfk_3` FOREIGN KEY (`current_winner_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auctions`
--

LOCK TABLES `auctions` WRITE;
/*!40000 ALTER TABLE `auctions` DISABLE KEYS */;
/*!40000 ALTER TABLE `auctions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `auto_bids`
--

DROP TABLE IF EXISTS `auto_bids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auto_bids` (
                             `auto_bid_id` int NOT NULL AUTO_INCREMENT,
                             `auction_id` int NOT NULL,
                             `bidder_id` int NOT NULL,
                             `max_bid` decimal(15,2) NOT NULL,
                             `increment` decimal(15,2) NOT NULL,
                             `status` enum('ACTIVE','COMPLETED','CANCELED') NOT NULL DEFAULT 'ACTIVE',
                             `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                             `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             PRIMARY KEY (`auto_bid_id`),
                             KEY `auction_id` (`auction_id`),
                             KEY `bidder_id` (`bidder_id`),
                             CONSTRAINT `auto_bids_ibfk_1` FOREIGN KEY (`auction_id`) REFERENCES `auctions` (`auction_id`) ON DELETE CASCADE,
                             CONSTRAINT `auto_bids_ibfk_2` FOREIGN KEY (`bidder_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `auto_bids`
--

LOCK TABLES `auto_bids` WRITE;
/*!40000 ALTER TABLE `auto_bids` DISABLE KEYS */;
/*!40000 ALTER TABLE `auto_bids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bids`
--

DROP TABLE IF EXISTS `bids`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bids` (
                        `bid_id` int NOT NULL AUTO_INCREMENT,
                        `auction_id` int NOT NULL,
                        `bidder_id` int NOT NULL,
                        `amount` decimal(15,2) NOT NULL,
                        `bid_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                        `is_auto_bid` tinyint(1) NOT NULL DEFAULT '0',
                        `status` enum('WINNING','OUTBID','WON','LOST') NOT NULL DEFAULT 'WINNING',
                        PRIMARY KEY (`bid_id`),
                        KEY `auction_id` (`auction_id`),
                        KEY `bidder_id` (`bidder_id`),
                        CONSTRAINT `bids_ibfk_1` FOREIGN KEY (`auction_id`) REFERENCES `auctions` (`auction_id`) ON DELETE CASCADE,
                        CONSTRAINT `bids_ibfk_2` FOREIGN KEY (`bidder_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bids`
--

LOCK TABLES `bids` WRITE;
/*!40000 ALTER TABLE `bids` DISABLE KEYS */;
/*!40000 ALTER TABLE `bids` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `item_attributes`
--

DROP TABLE IF EXISTS `item_attributes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_attributes` (
                                   `attr_id` int NOT NULL AUTO_INCREMENT,
                                   `item_id` int NOT NULL,
                                   `attr_key` varchar(100) NOT NULL,
                                   `attr_value` varchar(500) NOT NULL,
                                   PRIMARY KEY (`attr_id`),
                                   KEY `item_id` (`item_id`),
                                   CONSTRAINT `item_attributes_ibfk_1` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `item_attributes`
--

LOCK TABLES `item_attributes` WRITE;
/*!40000 ALTER TABLE `item_attributes` DISABLE KEYS */;
/*!40000 ALTER TABLE `item_attributes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `item_categories`
--

DROP TABLE IF EXISTS `item_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_categories` (
                                   `category_id` int NOT NULL AUTO_INCREMENT,
                                   `name` varchar(100) NOT NULL,
                                   `parent_id` int DEFAULT NULL,
                                   PRIMARY KEY (`category_id`),
                                   KEY `parent_id` (`parent_id`),
                                   CONSTRAINT `item_categories_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `item_categories` (`category_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `item_categories`
--

LOCK TABLES `item_categories` WRITE;
/*!40000 ALTER TABLE `item_categories` DISABLE KEYS */;
/*!40000 ALTER TABLE `item_categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `item_images`
--

DROP TABLE IF EXISTS `item_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_images` (
                               `image_id` int NOT NULL AUTO_INCREMENT,
                               `item_id` int NOT NULL,
                               `url` varchar(500) NOT NULL,
                               `is_primary` tinyint(1) NOT NULL DEFAULT '0',
                               `sort_order` int NOT NULL DEFAULT '0',
                               PRIMARY KEY (`image_id`),
                               KEY `item_id` (`item_id`),
                               CONSTRAINT `item_images_ibfk_1` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `item_images`
--

LOCK TABLES `item_images` WRITE;
/*!40000 ALTER TABLE `item_images` DISABLE KEYS */;
/*!40000 ALTER TABLE `item_images` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `items`
--

DROP TABLE IF EXISTS `items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `items` (
                         `item_id` int NOT NULL AUTO_INCREMENT,
                         `seller_id` int NOT NULL,
                         `category_id` int DEFAULT NULL,
                         `name` varchar(255) NOT NULL,
                         `description` text,
                         `starting_price` decimal(15,2) NOT NULL,
                         `status` enum('DRAFT','AVAILABLE','IN_AUCTION','SOLD','REMOVED') NOT NULL DEFAULT 'DRAFT',
                         `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (`item_id`),
                         KEY `seller_id` (`seller_id`),
                         KEY `category_id` (`category_id`),
                         CONSTRAINT `items_ibfk_1` FOREIGN KEY (`seller_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT,
                         CONSTRAINT `items_ibfk_2` FOREIGN KEY (`category_id`) REFERENCES `item_categories` (`category_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `items`
--

LOCK TABLES `items` WRITE;
/*!40000 ALTER TABLE `items` DISABLE KEYS */;
/*!40000 ALTER TABLE `items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
                                 `notif_id` int NOT NULL AUTO_INCREMENT,
                                 `user_id` int NOT NULL,
                                 `type` enum('BID_PLACED','OUTBID','AUCTION_WON','AUCTION_LOST','AUCTION_STARTED','AUCTION_ENDED','PAYMENT_RECEIVED','PAYMENT_DUE','SYSTEM') NOT NULL,
                                 `title` varchar(255) NOT NULL,
                                 `content` text NOT NULL,
                                 `is_read` tinyint(1) NOT NULL DEFAULT '0',
                                 `related_id` int DEFAULT NULL,
                                 `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`notif_id`),
                                 KEY `user_id` (`user_id`),
                                 CONSTRAINT `notifications_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
                            `payment_id` int NOT NULL AUTO_INCREMENT,
                            `auction_id` int NOT NULL,
                            `buyer_id` int NOT NULL,
                            `seller_id` int NOT NULL,
                            `amount` decimal(15,2) NOT NULL,
                            `status` enum('PENDING','COMPLETED','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
                            `paid_at` timestamp NULL DEFAULT NULL,
                            `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`payment_id`),
                            UNIQUE KEY `auction_id` (`auction_id`),
                            KEY `buyer_id` (`buyer_id`),
                            KEY `seller_id` (`seller_id`),
                            CONSTRAINT `payments_ibfk_1` FOREIGN KEY (`auction_id`) REFERENCES `auctions` (`auction_id`) ON DELETE RESTRICT,
                            CONSTRAINT `payments_ibfk_2` FOREIGN KEY (`buyer_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT,
                            CONSTRAINT `payments_ibfk_3` FOREIGN KEY (`seller_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payments`
--

LOCK TABLES `payments` WRITE;
/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
                         `user_id` int NOT NULL AUTO_INCREMENT,
                         `username` varchar(100) NOT NULL,
                         `password` varchar(255) NOT NULL,
                         `email` varchar(255) NOT NULL,
                         `full_name` varchar(255) DEFAULT NULL,
                         `phone` varchar(20) DEFAULT NULL,
                         `role` enum('USER','BIDDER','SELLER') NOT NULL DEFAULT 'USER',
                         `is_active` tinyint(1) NOT NULL DEFAULT '1',
                         `status` enum('ACTIVE','SUSPENDED','BANNED') NOT NULL DEFAULT 'ACTIVE',
                         `last_login` timestamp NULL DEFAULT NULL,
                         `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `username` (`username`),
                         UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wallet_transactions`
--

DROP TABLE IF EXISTS `wallet_transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_transactions` (
                                       `tx_id` int NOT NULL AUTO_INCREMENT,
                                       `wallet_id` int NOT NULL,
                                       `user_id` int NOT NULL,
                                       `type` enum('DEPOSIT','WITHDRAW','HOLD','RELEASE','PAYMENT','REFUND') NOT NULL,
                                       `amount` decimal(15,2) NOT NULL,
                                       `ref_auction_id` int DEFAULT NULL,
                                       `note` varchar(500) DEFAULT NULL,
                                       `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY (`tx_id`),
                                       KEY `wallet_id` (`wallet_id`),
                                       KEY `user_id` (`user_id`),
                                       KEY `ref_auction_id` (`ref_auction_id`),
                                       CONSTRAINT `wallet_transactions_ibfk_1` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`wallet_id`) ON DELETE RESTRICT,
                                       CONSTRAINT `wallet_transactions_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT,
                                       CONSTRAINT `wallet_transactions_ibfk_3` FOREIGN KEY (`ref_auction_id`) REFERENCES `auctions` (`auction_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wallet_transactions`
--

LOCK TABLES `wallet_transactions` WRITE;
/*!40000 ALTER TABLE `wallet_transactions` DISABLE KEYS */;
/*!40000 ALTER TABLE `wallet_transactions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wallets`
--

DROP TABLE IF EXISTS `wallets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallets` (
                           `wallet_id` int NOT NULL AUTO_INCREMENT,
                           `user_id` int NOT NULL,
                           `balance` decimal(15,2) NOT NULL DEFAULT '0.00',
                           `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           PRIMARY KEY (`wallet_id`),
                           UNIQUE KEY `user_id` (`user_id`),
                           CONSTRAINT `wallets_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wallets`
--

LOCK TABLES `wallets` WRITE;
/*!40000 ALTER TABLE `wallets` DISABLE KEYS */;
/*!40000 ALTER TABLE `wallets` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-04 14:41:36
