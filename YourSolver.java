package com.codenjoy.dojo.games.mollymage;

/*-
 * #%L
 * Codenjoy - it's a dojo-like platform from developers to developers.
 * %%
 * Copyright (C) 2012 - 2022 Codenjoy
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.codenjoy.dojo.client.Solver;
import com.codenjoy.dojo.services.Dice;
import com.codenjoy.dojo.services.Direction;
import com.codenjoy.dojo.services.Point;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static com.codenjoy.dojo.games.mollymage.Element.*;

/**
 * Author: your name
 * <p>
 * This is your AI algorithm for the game.
 * Implement it at your own discretion.
 * Pay attention to {@link YourSolverTest} - there is
 * a test framework for you.
 */
public class YourSolver implements Solver<Board> {

    public static int potionStepsRemained = 0;
    public static Random random = new Random();
    private static Direction setDirection = null;
    private static Direction wallDirection = null;
    private static Point hero = null;

    public final static List<Direction> ALL_DIRECTIONS = List.of(Direction.UP, Direction.RIGHT, Direction.LEFT, Direction.DOWN);

    private final Dice dice;
    private Board board;

    public YourSolver(Dice dice) {
        this.dice = dice;
    }

    @Override
    public String get(Board board) {
        this.board = board;
        if (board.isGameOver()) return "";

        hero = this.board.getHero();

        if (potionStepsRemained < 1
                && !hasElementInAnyDirection(hero, List.of(ElementUtils.perks), 3)
                && (hasGhostsOrEnemiesAround()
                || hasElementInAnyDirection(hero, getTargetElements(), 3)
                || hasElementInAnyDirection(hero, List.of(TREASURE_BOX), 2))) {
            potionStepsRemained = 5;
            wallDirection = null;
            setDirection = null;
            potionStepsRemained--;
            return List.of(Command.DROP_POTION, moveCommand()).toString();
        }
        potionStepsRemained--;
        return moveCommand();
    }

    private String moveCommand() {
        List<Point> allMovePoints = ALL_DIRECTIONS.stream()
                .map(direction -> direction.change(hero))
                .collect(Collectors.toList());

        List<Direction> perksDirections = new ArrayList<>();
        List<Direction> otherHeroesDirections = new ArrayList<>();
        List<Direction> boxesDirections = new ArrayList<>();
        List<Direction> possibleMoveDirections = new CopyOnWriteArrayList<>();

        // Check if the hero can move
        for (Point movePoint : allMovePoints) {
            if (openCell(movePoint) && safeCellToMove(movePoint)) {
                Direction direction = hero.direction(movePoint);

                // Check If the direction heads to dead road after potion
                // and don't take dangerous perks
                if (potionStepsRemained == 4 && (deadRoad(hero, direction, 3)
                        || hasElementInDirection(movePoint, direction, List.of(GHOST, GHOST_DEAD, OTHER_HERO, OTHER_HERO_POTION), 4))
                        || getBadPerks().contains(this.board.getAt(movePoint))) {
                    continue;
                }

                if (hasElementInDirection(movePoint, direction, getGoodPerks(), 15)) {
                    perksDirections.add(direction);
                }

                if (deadRoad(hero, hero.direction(movePoint), 1)) {
                    continue;
                }

                if (hasElementInDirection(movePoint, direction, List.of(OTHER_HERO, OTHER_HERO_POTION), 15)
                        && potionStepsRemained < 1) {
                    otherHeroesDirections.add(direction);
                }

                if (hasElementInDirection(movePoint, direction, List.of(TREASURE_BOX), 15)
                        && potionStepsRemained < 1) {
                    boxesDirections.add(direction);
                }

                possibleMoveDirections.add(direction);
            }
        }

        if (!perksDirections.isEmpty()) {
            return chooseAppropriateMove(perksDirections);
        }

        if (!otherHeroesDirections.isEmpty()) {
            return chooseAppropriateMove(otherHeroesDirections);
        }

        if (!boxesDirections.isEmpty()) {
            return chooseAppropriateMove(boxesDirections);
        }

        if (!possibleMoveDirections.isEmpty()) {
            return getBestMove(possibleMoveDirections);
        }

        // Check if the hero is already in the safe place
        if (safePosition()) {
            return Command.NONE;
        }

        return getRandomMove();
    }

    private String chooseAppropriateMove(List<Direction> directions) {
        wallDirection = null;
        if (setDirection == null || !directions.contains(setDirection)) {
            setDirection = directions.get(random.nextInt(directions.size()));
        }
        return Command.MOVE.apply(setDirection);
    }

    public boolean hasGhostsOrEnemiesAround() {
        int x = hero.getX();
        int y = hero.getY();
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (hasGhost(x + i, y + j) || hasOtherHero(x + i, y + j)) {
                    if (!hasWall(x + i, y) && !hasWall(x, y + i)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasElementInAnyDirection(Point point, List<Element> elements, int depth) {
        return ALL_DIRECTIONS.stream()
                .anyMatch(direction -> hasElementInDirection(direction.change(point), direction, elements, depth));
    }

    private boolean hasElementInDirection(Point point, Direction direction, List<Element> elements, int depth) {
        if (depth < 1) {
            return false;
        }
        Element element = this.board.getAt(point);
        if (elements.contains(element)) {
            return true;
        }
        return (element == NONE || element == BLAST)
                && hasElementInDirection(direction.change(point), direction, elements, --depth);
    }

    private boolean hasPotionInDirection(Point point, Direction direction, List<Element> potions, int depth) {
        Element element = this.board.getAt(point);
        if (depth < 1) {
            return false;
        }
        if (potions.contains(element)) {
            return true;
        }

        return !getBarriersForPotion().contains(element)
                && hasPotionInDirection(direction.change(point), direction, potions, --depth);
    }

    public String getBestMove(List<Direction> directions) {
        System.out.println("Choosing best direction to move");
        Direction backDirection = null;

        if (setDirection != null) {
            for (Direction direction : directions) {
                if (direction.equals(setDirection)) {

                    // If, while moving along a wall, the wall ends, it's time to turn.
                    if (wallDirection != null) {
                        Point wallPoint = wallDirection.change(board.getHero());
                        if (!hasWall(wallPoint.getX(), wallPoint.getY()) && directions.contains(wallDirection)) {
                            System.out.println("Turning to " + wallDirection + " because the wall ended");
                            setDirection = wallDirection;
                            wallDirection = null;
                            return Command.MOVE.apply(setDirection);
                        }
                    }

                    updateWallDirection(direction);

                    System.out.println("Moving with set direction " + setDirection);
                    return Command.MOVE.apply(direction);
                }
            }

            System.out.println("Clearing set direction " + setDirection);
            backDirection = setDirection.inverted();
            setDirection = null;
        }

        if (backDirection != null && directions.contains(backDirection) && directions.size() > 1) {
            System.out.println("Removing back direction " + backDirection);
            directions.remove(backDirection);
        }


        if (directions.size() > 1) {
            directions.stream().filter(direction -> hasElementInDirection(hero, direction,
                            List.of(POTION_TIMER_1, POTION_TIMER_2, GHOST, GHOST_DEAD, OTHER_HERO, OTHER_HERO_POTION), 3))
                    .findFirst().ifPresent(directions::remove);
        }

        setDirection = directions.get(random.nextInt(directions.size()));

        updateWallDirection(setDirection);
        return Command.MOVE.apply(setDirection);
    }

    private void updateWallDirection(Direction direction) {
        Direction clockwise = direction.clockwise();
        Direction counterClockwise = direction.counterClockwise();

        if (hasWall(clockwise.change(hero).getX(), clockwise.change(hero).getY())) {
            wallDirection = clockwise;
        } else if (hasWall(counterClockwise.change(hero).getX(), counterClockwise.change(hero).getY())) {
            wallDirection = counterClockwise;
        } else {
            wallDirection = null;
        }
        System.out.println("Wall direction is set to " + wallDirection);
    }


    public boolean openCell(Point point) {
        Element element = this.board.getAt(point.getX(), point.getY());
        return !hasBarrier(element);
    }

    public boolean safeCellToMove(Point point) {
        return ALL_DIRECTIONS.stream()
                .allMatch(direction ->
                        !hasElementInDirection(direction.change(point), direction, List.of(ElementUtils.ghosts), 1)
                                && !hasElementInDirection(direction.change(point), direction, getOtherHeroes(), 1)
                                && !hasPotionInDirection(direction.change(point), direction, List.of(POTION_TIMER_1, OTHER_HERO_POTION), 3));
    }

    public boolean safePosition() {
        return ALL_DIRECTIONS.stream()
                .allMatch(direction -> this.board.getAt(hero) != HERO_POTION
                        && potionStepsRemained != 4
                        && !hasElementInDirection(direction.change(hero), direction, List.of(ElementUtils.ghosts), 1)
                        && !hasPotionInDirection(direction.change(hero), direction, List.of(POTION_TIMER_1, POTION_TIMER_2), 3));
    }

    public boolean tempSafePosition() {
        return ALL_DIRECTIONS.stream()
                .noneMatch(direction -> hasPotionInDirection(direction.change(hero), direction, List.of(POTION_TIMER_1), 3));
    }

    public boolean deadRoad(Point point, Direction direction, int depth) {
        System.out.printf("Checking if %s doest head to dead road\n", direction);

        Direction backDirection = direction.inverted();
        Point nextPoint = direction.change(point);

        boolean isDeadRoad = ALL_DIRECTIONS.stream()
                .filter(d -> d != backDirection)
                .allMatch(d -> hasBarrier(board.getAt(d.change(nextPoint))));

        if (!isDeadRoad && depth > 1) {
            isDeadRoad = ALL_DIRECTIONS.stream()
                    .filter(d -> d != backDirection && d != direction)
                    .allMatch(d -> hasBarrier(board.getAt(d.change(nextPoint)))
                            || hasElementInDirection(nextPoint, d, List.of(GHOST, GHOST_DEAD, OTHER_HERO, OTHER_HERO_POTION), 2));

            return isDeadRoad && deadRoad(nextPoint, direction, --depth);
        }
        return isDeadRoad;
    }

    private String getRandomMove() {
        List<Direction> directions = new CopyOnWriteArrayList<>(ALL_DIRECTIONS);

        // remove barrier, blast, dead-end(if hero put potion) directions
        directions.stream()
                .filter(direction -> {
                    Point nextPoint = direction.change(hero);
                    return getBarriersToMove().contains(this.board.getAt(nextPoint))
                            || ALL_DIRECTIONS.stream().anyMatch(d ->
                            hasPotionInDirection(d.change(nextPoint), d, List.of(POTION_TIMER_1), 3))
                            || this.board.getAt(hero) == HERO_POTION && deadRoad(hero, direction, 2);
                })
                .forEach(directions::remove);

        if (!directions.isEmpty()) {
            System.out.println("Choosing random move from filtered directions: " + directions);
            setDirection = null;
            wallDirection = null;
            return Command.MOVE.apply(directions.get(random.nextInt(directions.size())));
        } else if (tempSafePosition()) {
            return Command.NONE;
        }
        System.out.println("Choosing random move from unfiltered directions: " + ALL_DIRECTIONS + " This shouldn't happen!!!");
        return Command.MOVE.apply(ALL_DIRECTIONS.get(random.nextInt(ALL_DIRECTIONS.size())));
    }

    public boolean hasBarrier(Element e) {
        return Arrays.stream(ElementUtils.barriers).anyMatch(barrier -> barrier == e);
    }

    public boolean hasGhost(int x, int y) {
        Element e = this.board.getAt(x, y);
        return Arrays.stream(ElementUtils.ghosts).anyMatch(ghost -> ghost == e);
    }

    public boolean hasOtherHero(int x, int y) {
        Element e = this.board.getAt(x, y);
        return e == Element.OTHER_HERO
                || e == Element.OTHER_HERO_POTION
                || e == Element.ENEMY_HERO
                || e == Element.ENEMY_HERO_POTION;
    }

    private boolean hasWall(int x, int y) {
        return this.board.getAt(x, y) == Element.WALL;
    }

    private List<Element> getGoodPerks() {
        return List.of(POTION_COUNT_INCREASE, POTION_IMMUNE, POISON_THROWER, POTION_EXPLODER);
    }

    private List<Element> getBadPerks() {
        return List.of(POTION_REMOTE_CONTROL, POTION_BLAST_RADIUS_INCREASE);
    }

    private List<Element> getTargetElements() {
        return List.of(OTHER_HERO,
                OTHER_HERO_POTION,
                ENEMY_HERO,
                ENEMY_HERO_POTION,
                GHOST,
                GHOST_DEAD
        );
    }

    private List<Element> getBarriersForPotion() {
        return List.of(
                WALL,
                TREASURE_BOX);
    }

    private List<Element> getBarriersToMove() {
        return List.of(
                GHOST,
                GHOST_DEAD,
                WALL,
                POTION_TIMER_1,
                POTION_TIMER_2,
                POTION_TIMER_3,
                POTION_TIMER_4,
                POTION_TIMER_5,
                TREASURE_BOX,
                OTHER_HERO,
                OTHER_HERO_POTION,
                ENEMY_HERO,
                ENEMY_HERO_POTION);
    }

    private List<Element> getOtherHeroes() {
        return List.of(
                OTHER_HERO,
                OTHER_HERO_POTION,
                ENEMY_HERO,
                ENEMY_HERO_POTION);
    }
}