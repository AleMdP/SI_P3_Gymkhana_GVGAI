package si2026.alejandrodelpozoalu.p04;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import java.util.*;

public class Practica_03 extends AbstractPlayer {
    int bloque;
    int columnas, filas;
    int[][] grid;
    int[] meta;
    
    final int SUELO = 0, ARBOL = 1, AGUA = 2, META = 3, TRAMPOLIN = 4, PLATAFORMA = 5, NENUFAR = 6, SPAWNER_I = 7, SPAWNER_D = 8;
    
    Set<String> trampolinesInseguros = new HashSet<>();
    Set<String> filasSpawnersI, filasSpawnersD, filasSpawners;

    public Practica_03(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        bloque = stateObs.getBlockSize();
        filas = stateObs.getWorldDimension().height / bloque;
        columnas = stateObs.getWorldDimension().width / bloque;
        grid = new int[columnas][filas];

        filasSpawnersI = new HashSet<>();
        filasSpawnersD = new HashSet<>();
        filasSpawners = new HashSet<>();
        
        ArrayList<Observation>[][] obs = stateObs.getObservationGrid();
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < filas; j++) {
                for (Observation o : obs[i][j]) { /// CAMBIAR PARA QUE NO USE EL FOR
                    if (o.category == 4) {
                        if (o.itype == 7) {
                        	for(int k = i; k<columnas; k++) {
                        		for (Observation ob : obs[k][j]) {
                        			if (ob.category == 4 && ob.itype == 0) break;
                                	filasSpawnersI.add(k+","+j);
                        		}
                        	}
                        }
                        else if (o.itype == 8) {
                        	for(int k = i; k>=0; k--) {
                        		for (Observation ob : obs[k][j]) {
                        			if (ob.category == 4 && ob.itype == 0) break;
                                	filasSpawnersD.add(k+","+j);
                        		}
                        	}
                        }
                    }
                    else if(o.category == 2) meta = new int[] {i, j};
                }
            }
        }
        filasSpawners.addAll(filasSpawnersD);
        filasSpawners.addAll(filasSpawnersI);
    }

    @Override
    public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        analizarMapa(stateObs);
        
//        System.out.println(filasSpawnersI + ", " + filasSpawnersD);
        
        Vector2d posAvatar = stateObs.getAvatarPosition();
        int x = (int) ((posAvatar.x + (bloque / 2.0)) / bloque);
        int y = (int) ((posAvatar.y + (bloque / 2.0)) / bloque);
        
        ACTIONS sigA = calcularSiguienteAccion(stateObs, x, y);
//        System.out.println(sigA);

        // Si hay nenufar arriba, estoy en nenufar y la meta esta arriba, voy a ese nenufar
        if((grid[x][y-1] == NENUFAR || grid[x][y-1] == PLATAFORMA) && y > meta[1] && grid[x][y] == NENUFAR) {
        	return ACTIONS.ACTION_UP;
        }
        
        // Si quiero ir a tal direccion pero hay agua:
        boolean esperarNenufar = false;
        int mov = 0;
        if(sigA == ACTIONS.ACTION_DOWN) {
    		mov = 1;
        	if(grid[x][y+1] == AGUA) {
        		esperarNenufar = true;
        	}
        }        
        else if(sigA == ACTIONS.ACTION_UP) {
    		mov = -1;
        	if(grid[x][y-1] == AGUA) {
        		esperarNenufar = true;
        	}
        }
        else if(sigA == ACTIONS.ACTION_RIGHT) {
        	if(grid[x+1][y] == AGUA || grid[x+1][y] == NENUFAR) {
        		return ACTIONS.ACTION_NIL;
        	}
        }
        else if(sigA == ACTIONS.ACTION_LEFT) {
        	if(grid[x-1][y] == AGUA || grid[x-1][y] == NENUFAR) {
        		return ACTIONS.ACTION_NIL;
        	}
        }
        
        // Si el trampolin chocaria con nenufar
        if((sigA == ACTIONS.ACTION_DOWN || sigA == ACTIONS.ACTION_UP)  && trampolinBloqueadoPorNenufar(stateObs, x, y+mov)) {
        	return ACTIONS.ACTION_NIL;
        }

        // Si tiene que esperar al nenufar
        if (esperarNenufar) {
            if (esperarNenufar(stateObs, x, y + mov)) return sigA;
            return ACTIONS.ACTION_NIL;
        }

        return sigA;
    }

    private void analizarMapa(StateObservation stateObs) {
        ArrayList<Observation>[][] obs = stateObs.getObservationGrid();
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < filas; j++) {
                grid[i][j] = SUELO;
                for (Observation o : obs[i][j]) {
                    if (o.category == 2) grid[i][j] = META;
                    else if (o.category == 6) {
                    	if(o.itype == 12) grid[i][j] = PLATAFORMA;
                    	else if(o.itype == 10 || o.itype == 11) grid[i][j] = NENUFAR;
                    }
                    else if (o.category == 4) {
                        if (o.itype == 0) grid[i][j] = ARBOL;
                        else if (o.itype == 3) grid[i][j] = AGUA;
                        else if (o.itype == 7) grid[i][j] = SPAWNER_I;
                        else if (o.itype == 8) grid[i][j] = SPAWNER_D;
                        else if (o.itype >= 14 && o.itype <= 17) grid[i][j] = TRAMPOLIN;
                    }
                }
            }
        }
    }

    private ACTIONS calcularSiguienteAccion(StateObservation stateObs, int startX, int startY) {
        Queue<Nodo> cola = new LinkedList<>();
        boolean[][] visitado = new boolean[columnas][filas];
        cola.add(new Nodo(startX, startY, null, null));
        visitado[startX][startY] = true;

        while (!cola.isEmpty()) {
            Nodo curr = cola.poll();

            if (grid[curr.x][curr.y] == META) {
                return obtenerPrimerMovimiento(curr);
            }

            int[][] dirs = {{0,1}, {1,0}, {-1,0}, {0,-1}};
            ACTIONS[] acts = {ACTIONS.ACTION_DOWN, ACTIONS.ACTION_RIGHT, ACTIONS.ACTION_LEFT, ACTIONS.ACTION_UP};

            for (int i = 0; i < 4; i++) {
                int nx = curr.x + dirs[i][0];
                int ny = curr.y + dirs[i][1];

                if (nx < 0 || nx >= columnas || ny < 0 || ny >= filas || visitado[nx][ny]) continue;

                // CASO TRAMPOLÍN
                if (grid[nx][ny] == TRAMPOLIN) {
                    String clave = nx + "," + ny;
//                    System.out.println(trampolinesInseguros);
                    if(!trampolinesInseguros.contains(clave)) {
                        int[] destino = calcularDestinoTrampolin(stateObs, nx, ny);
                        if (destino == null) {
//                        	System.out.println("null");
                        	trampolinesInseguros.add(clave);
                        }
                        if (destino != null && !visitado[destino[0]][destino[1]]) {
//                        	System.out.println(nx + ", " + ny);
//                            System.out.println(destino[0] + ", " + destino[1]);
//                            System.out.println();
                            visitado[nx][ny] = true;
                            Nodo nodoDest = new Nodo(destino[0], destino[1], curr, acts[i]);
                            visitado[destino[0]][destino[1]] = true;
                            cola.add(nodoDest);
                        }
                        continue;
                    }
                }

                // CASO SPAWNERS
                boolean esOrillaDeSpawner = false;
                for(String j:filasSpawners) {
                	if ((nx + "," + ny).equals(j)) esOrillaDeSpawner = true;
                }
                if (esOrillaDeSpawner) {
//                	System.out.println("Esperando orilla");
                	visitado[nx][ny] = true;
                    cola.add(new Nodo(nx, ny, curr, acts[i]));
                    continue;
                }

                // CASO SUELO / PLATADORMA / META
                if (grid[nx][ny] == SUELO || grid[nx][ny] == PLATAFORMA || grid[nx][ny] == META) {
                    visitado[nx][ny] = true;
                    cola.add(new Nodo(nx, ny, curr, acts[i]));
                }
            }
        }
        return ACTIONS.ACTION_NIL;
    }
    
    private boolean trampolinBloqueadoPorNenufar(StateObservation stateObs, int tx, int ty) {
        ArrayList<Observation> obs = stateObs.getObservationGrid()[tx][ty];
        int itype = -1;
        for (Observation o : obs) if (o.itype >= 14 && o.itype <= 17) itype = o.itype;
        if (itype == -1) return false;

        boolean vertical = (itype == 14 || itype == 15);
        boolean haciaAbajo = (itype == 14);

        if (!vertical) return false;

        int[] colsAComprobar = {tx - 1, tx, tx + 1};

        int yInicio = haciaAbajo ? ty + 1 : 0;
        int yFin    = haciaAbajo ? filas - 1 : ty - 1;

        for (int col : colsAComprobar) {
            if (col < 0 || col >= columnas) continue;
            for (int fila = yInicio; fila <= yFin; fila++) {
                for (Observation o : stateObs.getObservationGrid()[col][fila]) {
                    if (o.category == 6 && (o.itype == 10 || o.itype == 11)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int[] calcularDestinoTrampolin(StateObservation stateObs, int tx, int ty) {
        ArrayList<Observation> obs = stateObs.getObservationGrid()[tx][ty];
        int itype = -1;
        for (Observation o : obs) if (o.itype >= 14 && o.itype <= 17) itype = o.itype;
        if (itype == -1) return null;

        int dx = 0, dy = 0;
        if (itype == 14) dy = 1; else if (itype == 15) dy = -1;
        else if (itype == 16) dx = 1; else if (itype == 17) dx = -1;

        int i = 1;
        while(true) {
            int lx = tx + dx * i, ly = ty + dy * i;
//            System.out.println(lx + " de " + columnas);
//            System.out.println(i);
//            System.out.println(lx + ", " + ly);
//            System.out.println(grid[lx][ly]);
//            System.out.println();
            if (lx < 0 || lx >= columnas || ly < 0 || ly >= filas) break;
            
            if (grid[lx][ly] == PLATAFORMA || grid[lx][ly] == META) return new int[]{lx, ly};
            if (grid[lx][ly] == ARBOL) {
            	if(grid[lx-dx][ly-dy] != AGUA) return new int[] {lx-dx, ly-dy};
            	else return null;
            }
            if (grid[lx][ly] == TRAMPOLIN) {
            	grid[tx][ty] = SUELO;
            	return calcularDestinoTrampolin(stateObs, lx, ly);
            }
            
            i++;
        }
        return null; // Cae al agua o fuera
    }

    private boolean esperarNenufar(StateObservation stateObs, int gx, int gy) {
        if (gx < 0 || gx >= columnas || gy < 0 || gy >= filas) return false;
        
        for (Observation o : stateObs.getObservationGrid()[gx][gy]) {
            if (o.category == 6 && (o.itype == 10 || o.itype == 11)) {
//            	System.out.println("APAP");
                if (gx == stateObs.getAvatarPosition().x/bloque) return true;
            }
        }
        return false;
    }

    private ACTIONS obtenerPrimerMovimiento(Nodo n) {
        while (n.parent != null && n.parent.parent != null) n = n.parent;
//        System.out.println(n.action);
        return n.action;
    }

    private class Nodo {
        int x, y; Nodo parent; ACTIONS action;
        Nodo(int x, int y, Nodo p, ACTIONS a) { this.x = x; this.y = y; this.parent = p; this.action = a; }
    }
}


/*
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
	                else if (categoria == 6){
	                	int tipo = mapa[j][i].get(0).itype;
	                	if(tipo == 10) linea += "Ni";
	                	else if(tipo == 11) linea += "Nd";
	                	else if(tipo == 12) linea += "P ";
	                } 
	                else linea += categoria + "O";
				} else {
					linea += ". ";
				}
			}
			System.out.println(linea);
        }
*/
