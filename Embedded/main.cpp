/*
 * Vibration.cpp
 *
 * Created: 12/22/2024 8:57:17 AM
 * Author : Pham Tung Lam
 */ 
#define F_CPU 8000000UL

#include <avr/io.h>
#include <avr/interrupt.h>
#include <avr/sleep.h>
#include <util/delay.h>

void setup_interrupts() {
	// Bat ngoai ngoai INT1
	MCUCR |= (1 << ISC11); //Ngat canh len
	MCUCR &= ~(1 << ISC10);
	
	GICR |= (1 << INT1); // Bat INT1
	sei(); // Ngat toan cuc
}

void setup_io() {
	DDRC |= (1 << PC0) | (1 << PC1);   // PWRKEY output
	PORTC &= ~(1 << PC1);
	PORTC &= ~(1 << PC0); // PC1 = 0
	DDRB |= (1 << PB1);   // UBLOX output
	PORTB &= ~(1 << PB1); // PB1 = 0
	DDRD &= ~(1 << PD3);  // PD3 input
	PORTD |= (1 << PD3);  // pull-up registor
}

void sleep_mode_init() {
	MCUCR &= ~(1 << SM1);  // SM1 = 0, SM0 = 0, SM2 = 0 => ide
	MCUCR &= ~(1 << SM0); 
	MCUCR &= ~(1 << SM2);
	sleep_enable();
}

ISR(INT1_vect) {
}

int main() {
	setup_io();
	setup_interrupts();
	sleep_mode_init();
	while (1) {
		if(PIND & (1 << PD3))
		{
			PORTB |= (1 << PB1); //Turn on ublox
			PORTC |= (1 << PC1);
			PORTC |= (1 << PC0); // Turn on SC20
			_delay_ms(3000); // Wait for 3 seconds
			PORTC &= ~(1 << PC0);
			_delay_ms(3000);
			 
		}
		else { // PD3 is low
			uint8_t stable_low = 1;
			for (uint8_t i = 0; i < 100; i++) { // Check for 30 seconds (300 x 100ms)
				if (PIND & (1 << PD3)) {
					stable_low = 0;
					break;
				}
				_delay_ms(100);
			}
			if (stable_low) { //30s
				PORTB &= ~(1 << PB1);
				PORTC |= (1 << PC0); // Turn PC1 on for 10 seconds
				_delay_ms(10000);
				PORTC &= ~(1 << PC0);
				PORTC &= ~(1 << PC1);
				sleep_mode(); // Enter sleep mode
			}
		}
	}
}

