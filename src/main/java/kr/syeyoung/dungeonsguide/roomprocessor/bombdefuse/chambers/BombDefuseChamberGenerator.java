/*
 *     Dungeons Guide - The most intelligent Hypixel Skyblock Dungeons Mod
 *     Copyright (C) 2021  cyoung06
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.chambers;

import kr.syeyoung.dungeonsguide.roomprocessor.bombdefuse.RoomProcessorBombDefuseSolver;

public interface BombDefuseChamberGenerator {
    boolean match(BDChamber left, BDChamber right);

    String getName();

    ChamberProcessor createLeft(BDChamber left, RoomProcessorBombDefuseSolver solver);
    ChamberProcessor createRight(BDChamber right, RoomProcessorBombDefuseSolver solver);
}