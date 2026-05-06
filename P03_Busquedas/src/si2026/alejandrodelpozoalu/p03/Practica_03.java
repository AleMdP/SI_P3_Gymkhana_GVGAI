package si2026.alejandrodelpozoalu.p03;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Practica_03 extends AbstractPlayer {

	int bloque, filas, columnas;
	
	int[] salida = new int[2];
	
	int accion = 0;
	Random r = new Random();
	List <ACTIONS> acciones;
	
	
    public Practica_03(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
    	bloque = stateObs.getBlockSize();
        filas = stateObs.getWorldDimension().height / bloque;
        columnas = stateObs.getWorldDimension().width / bloque;
        
        acciones = new LinkedList<ACTIONS>();
		acciones.add(ACTIONS.ACTION_NIL);
		acciones.add(ACTIONS.ACTION_LEFT);
		acciones.add(ACTIONS.ACTION_RIGHT);
		acciones.add(ACTIONS.ACTION_UP);
		acciones.add(ACTIONS.ACTION_DOWN);
        
        System.out.println("Mapa: [" + columnas + " x " + filas + "]");
        ArrayList<Observation>[][] mapa = stateObs.getObservationGrid();
        for(int i = 0; i< filas; i++) {
			String linea = "";
			for(int j = 0; j<columnas; j++) {
				if(mapa[j][i].size() > 0) {
					int categoria = mapa[j][i].get(0).category;
	                
	                if (categoria == 0) linea += "Y ";
	                else if (categoria == 2) {
	                	salida[0] = j;
	                	salida[1] = i;
	                	linea += "G ";
	                }
	                else if (categoria == 4) {
	                	int tipo = mapa[j][i].get(0).itype;
	                	if(tipo == 0) {
		                	linea += "W ";
	                	}
	                	else if(tipo == 3) {
	                		linea += "A ";
	                	}
	                	else if(tipo == 7) {
	                		linea += "Si";
	                	}
	                	else if(tipo == 8) {
	                		linea += "Sd";
	                	}
	                	else if(tipo == 14) {
	                		linea += "Tb";
	                	}
	                	else if(tipo == 15) {
	                		linea += "Ta";
	                	}
	                	else if(tipo == 16) {
	                		linea += "Td";
	                	}
	                	else if(tipo == 17) {
	                		linea += "Ti";
	                	}
	                	else linea += tipo + "U";
	                }
	                else if (categoria == 6) {
	                	int tipo = mapa[j][i].get(0).itype;
	                	if(tipo == 12) {
	                		linea += "P ";
	                	}
	                	else linea += tipo + "M";
	                }
	                else linea += categoria + "O";
				} else {
					linea += ". ";
				}
			}
			System.out.println(linea);
        }
    }

    @Override
    public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
    	return ACTIONS.ACTION_NIL;
    }
}

