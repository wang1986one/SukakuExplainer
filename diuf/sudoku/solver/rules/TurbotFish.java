package diuf.sudoku.solver.rules;

import java.util.*;
import diuf.sudoku.*;
import diuf.sudoku.solver.*;
import diuf.sudoku.tools.*;


/**
 * Implementation of Turbot Fish technique solver.
 */
public class TurbotFish implements IndirectHintProducer {

    @Override
    public void getHints(Grid grid, HintsAccumulator accu) throws InterruptedException {
		int[][] Sets = {
			// Skyscrapers
			{1, 1},
			{2, 2},
			// Two-string Kites
			{2, 1},
			// Turbot Fishes and Empty Rectangles
			{1, 0},
			{2, 0},
		};
		List<TurbotFishHint> hintsFinal = new ArrayList<TurbotFishHint>();
		List<TurbotFishHint> hintsStart;
        for (int i = 0; i < 5 ; i++) {	
			hintsStart = getHints(grid, Sets[i][0], Sets[i][1]);
			for (TurbotFishHint hint : hintsStart)
				hintsFinal.add(hint);
		}
		// Sort the result
		Collections.sort(hintsFinal, new Comparator<TurbotFishHint>() {
			public int compare(TurbotFishHint h1, TurbotFishHint h2) {
				double d1 = h1.getDifficulty();
				double d2 = h2.getDifficulty();
				//sort according to difficulty in ascending order
				if (d1 < d2)
					return -1;
				else if (d1 > d2)
					return 1;
				//sort according to number of eliminations in descending order
				return h2.getEliminationsTotal() - h1.getEliminationsTotal();
			}
		});
		for (TurbotFishHint hint : hintsFinal)
			accu.add(hint);
}

    private Grid.Region shareRegionOf(Grid grid,
            Cell start, Cell bridge1, Cell bridge2, Cell end) {
        if (bridge1.getX() == bridge2.getX()) {
            return (Grid.Column)Grid.getRegionAt(2,bridge1.getIndex());
        } else if (bridge1.getY() == bridge2.getY()) {
            return (Grid.Row)Grid.getRegionAt(1,bridge1.getIndex());
        } else if (bridge1.getB() == bridge2.getB()) {
            return (Grid.Block)Grid.getRegionAt(0,bridge1.getIndex());
        } else return null;
    }

    private List<TurbotFishHint> getHints(Grid grid, int base, int cover)
            /*throws InterruptedException*/ {
		List<TurbotFishHint> result = new ArrayList<TurbotFishHint>();
		int e = 0;
		boolean emptyRectangle = false;
        for (int digit = 1; digit <= 9; digit++) {
            Grid.Region[] baseRegions = Grid.getRegions(base);
            Grid.Region[] coverRegions = Grid.getRegions(cover);
            for (int i1 = 0; i1 < baseRegions.length; i1++) {
				Grid.Region baseRegion = baseRegions[i1];
				BitSet baseRegionPotentials = baseRegion.getPotentialPositions(grid, digit);
				if (baseRegionPotentials.cardinality() != 2)
					continue;
				for (int i2 = (base == cover ? i1+1 : 0); i2 < coverRegions.length; i2++) {
					// For each set in sets
					Grid.Region coverRegion = coverRegions[i2];
					BitSet coverRegionPotentials = coverRegion.getPotentialPositions(grid, digit);
					int coverRegionPotentialsCardinality = coverRegionPotentials.cardinality(); 
					// For a strong link Cardinality == 2 in region, for an empty rectangle region is a block 
					// with cardinality >2 (or it will be a strong link in a block i.e turbot fish ) 
					// and cardinality <6 (because we need 4 empty cells in the region.Rectangle cells)
					if (coverRegionPotentialsCardinality == 2 || (cover == 0 && coverRegionPotentialsCardinality > 2 && coverRegionPotentialsCardinality < 6) ){
						emptyRectangle = false;
						if (coverRegionPotentialsCardinality > 2) {
							for (e = 0; e < 9; e++) {
								BitSet rectangle = (BitSet)coverRegionPotentials.clone();
								rectangle.and(coverRegion.Rectangle(e));
								//confirm if we have an empty rectangle
								//block has 9 cells: 4 "Cross" cells, 4 "Rectangle" cells and 1 "Heart" cell
								//9 configurations for each block depending on "Heart" cell
								if (rectangle.cardinality() == 0) {
									emptyRectangle = true;
									break;
								}
							}
							if (!emptyRectangle)
								continue;
						}							
						// Strong links found (Conjugate pairs found)
						// Check whether positions may in the same region or not (form a weak link)
						int p1, p2;
						Cell[] cells = new Cell[4];
								// region 1
								cells[0] = baseRegion.getCell(p1 = baseRegionPotentials.nextSetBit(0));
								cells[1] = baseRegion.getCell(baseRegionPotentials.nextSetBit(p1 + 1));										
								// region 2
								if (emptyRectangle) {
									cells[2] = coverRegion.getCell(coverRegion.Heart(e));
									cells[3] = coverRegion.getCell(coverRegion.Heart(e));										
								}
								else {
									cells[2] = coverRegion.getCell(p2 = coverRegionPotentials.nextSetBit(0));
									cells[3] = coverRegion.getCell(coverRegionPotentials.nextSetBit(p2 + 1));
								}
						// Cells cannot be same
						boolean next = false;
						for (int i = 0; i < 3; i++) {
							for (int j = i + 1; j < (emptyRectangle ? 3 : 4); j++) {
								if (cells[i].equals(cells[j])) {
									next = true;
									break;
								}
							}
						}
						if (next) continue;
						if (emptyRectangle)
							if (cells[0].getB() == cells[2].getB() || cells[1].getB() == cells[2].getB())
								continue;
						Grid.Region shareRegion;
						Cell start, end, bridge1, bridge2;
						for (int i = 0; i < 2; i++) {
							for (int j = 2; j < (emptyRectangle ? 3 : 4); j++) {
								if ((shareRegion = shareRegionOf(grid,
											start = cells[i],
											bridge1 = cells[1 - i],
											bridge2 = cells[j],
											end = cells[5 - j])) != null &&
										!shareRegion.equals(baseRegion) && !shareRegion.equals(coverRegion)) {
									// Turbot fish found
									TurbotFishHint hint = createHint(grid, digit, start, end, bridge1, bridge2,
											baseRegion, coverRegion, shareRegion, emptyRectangle);
									if (hint.isWorth())
										result.add(hint);
								}
							} // for int j = 0..2
						} // for int i = 0..2
					} // if baseRegionPotentials.cardinality() == 2 && coverRegionPotentials.cardinality() == 2
				}	
            }
        }
		return result;
    }

    private TurbotFishHint createHint(Grid grid, int value, Cell start, Cell end, Cell bridgeCell1, Cell bridgeCell2,
            Grid.Region baseSet, Grid.Region coverSet, Grid.Region shareRegion, boolean emptyRectangle) {
        // Build list of removable potentials
        Map<Cell,BitSet> removablePotentials = new HashMap<>();
		int eliminationsTotal = 0;
        //Set<Cell> victims = new LinkedHashSet<>(start.getVisibleCells());
        CellSet victims = new CellSet(start.getVisibleCells());
        victims.retainAll(end.getVisibleCells());
        victims.remove(start);
        victims.remove(end);
		if (emptyRectangle){
			//victims.removeAll(coverSet.getCellSet());
			//victims.removeAll(baseSet.getCellSet());
			victims.bits.andNot(coverSet.regionCellsBitSet);
			victims.bits.andNot(baseSet.regionCellsBitSet);
		}
        for (Cell cell : victims) {
            if (grid.hasCellPotentialValue(cell.getIndex(), value)) {
				eliminationsTotal++;
                removablePotentials.put(cell, SingletonBitSet.create(value));
			}
        }
		Cell[] emptyRectangleCells= new Cell[5];
		if (emptyRectangle){
			int j = 0;
			for (int i = 0; i < 9 ; i++) {
				Cell CrossCell = coverSet.getCell(i);
				if (grid.hasCellPotentialValue(CrossCell.getIndex(), value))
					emptyRectangleCells[j++] = CrossCell;
			}
		}
        // Create hint
        return new TurbotFishHint(this, removablePotentials,
                start, end, bridgeCell1, bridgeCell2, value, baseSet, coverSet, shareRegion, emptyRectangle, emptyRectangleCells, eliminationsTotal);
    }

    @Override
    public String toString() {
        return "Turbot Fishes";
    }
}
